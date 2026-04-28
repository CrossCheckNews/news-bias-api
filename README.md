# news-bias-api

동일한 이슈를 다른 나라·성향의 언론사들이 어떻게 다르게 보도하는지 비교할 수 있는 뉴스 관점 비교 서비스의 백엔드 API입니다.

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.x |
| ORM | Spring Data JPA |
| Database | H2 (In-Memory, 개발용) |
| RSS Parser | Rome 2.1 |
| AI | Google Gemini API (`gemini-2.0-flash`) |
| Auth | JWT (JJWT 0.12) |
| Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Gradle |

---

## 핵심 기능

### 5단계 뉴스 처리 파이프라인

```
[1] RSS 수집      FeedSource enum → RssFetchService → ArticleCandidate
[2] 정규화        URL 정규화, HTML 제거, 헤드라인 소문자화 → NormalizedArticleCandidate
[3] 중복 제거     rssGuid → normalizedUrl → dedupeKey(SHA-256) 3단계 우선순위
[4] 클러스터링    Jaccard 유사도 + Union-Find → 다수 언론사 헤드라인 → Topic 자동 생성
[5] AI 요약       헤드라인 + description → Gemini → Topic.aiSummary
```

`POST /api/v1/pipeline/collect` 단일 요청으로 전체 파이프라인을 실행합니다.

---

## 패키지 구조

```
com.crosschecknews.api
├── client/           외부 API 클라이언트
│   └── GeminiClient          Gemini REST 호출 (Spring RestClient)
│
├── config/           애플리케이션 설정
│   ├── DataInitializer       서버 시작 시 FeedSource → Publisher/PublisherFeed 시딩
│   └── WebConfig             CORS 설정
│
├── controller/       HTTP 진입점 (요청 수신·응답 반환만 담당)
│   ├── ArticleController     GET /api/v1/articles
│   ├── AuthController        POST /api/v1/auth/login
│   ├── CollectController     POST /api/v1/collect/**
│   ├── PipelineController    POST /api/v1/pipeline/collect
│   ├── PublisherController   CRUD /api/v1/publishers
│   └── TopicController       CRUD + 비교 + 클러스터링 + 요약 /api/v1/topics
│
├── domain/           JPA 엔티티 & 도메인 enum
│   ├── Article               수집된 뉴스 기사
│   ├── Publisher             언론사 (이름, 국가, 정치성향)
│   ├── PublisherFeed         언론사별 RSS 피드 URL
│   ├── Topic                 동일 이슈를 묶는 토픽
│   ├── TopicArticle          Topic ↔ Article N:M 연결
│   ├── FeedSource            RSS 피드 열거형 (코드·메타데이터 포함)
│   ├── Category              기사 분류 (WORLD 등)
│   ├── Country               언론사 국가
│   ├── PoliticalLeaning      정치성향 (CONSERVATIVE / PROGRESSIVE)
│   └── TopicStatus           토픽 상태 (PENDING / ACTIVE / ARCHIVED)
│
├── dto/              요청·응답·파이프라인 중간 객체
│   ├── ArticleCandidate          [Stage 1] RSS 파싱 결과
│   ├── NormalizedArticleCandidate [Stage 2] 정규화 후 중간 객체
│   ├── FeedCollectResult         피드별 수집 결과
│   ├── FetchAndSaveResult        수집+저장 집계 결과
│   ├── ClusteringRequest/Result  클러스터링 요청·결과
│   ├── SummarizeResponse         AI 요약 응답
│   ├── TopicComparisonResponse   언론사 비교 응답 (flat + 그룹)
│   └── PipelineRequest/Result    파이프라인 요청·결과
│
├── exception/        예외 정의 및 전역 처리
│   ├── GlobalExceptionHandler    404·401·409·422·502 통일 처리
│   ├── ResourceNotFoundException  404
│   ├── InvalidCredentialsException 401
│   ├── RssFetchException         502 (RSS 파싱 실패)
│   └── GeminiException           502 (AI 호출 실패)
│
├── repository/       JPA 조회 인터페이스
│   ├── ArticleRepository         중복 체크 3종 + 클러스터링 후보 쿼리
│   ├── TopicRepository           status 필터 + aiSummary 없는 토픽 조회
│   ├── TopicArticleRepository    토픽-기사 연결 조회
│   ├── PublisherRepository
│   └── PublisherFeedRepository
│
└── service/          비즈니스 로직 (각 서비스는 단일 책임)
    ├── RssFetchService             [Stage 1] Rome으로 RSS XML 파싱
    ├── RssCollectService           [Stage 1] FeedSource 순회·에러 격리
    ├── ArticleNormalizationService [Stage 2] URL/헤드라인 정규화, dedupeKey 생성
    ├── ArticleDeduplicationService [Stage 3] 3단계 우선순위 중복 판별
    ├── ArticleSaveService          [Stage 1~3] 수집→정규화→중복제거→저장 조합
    ├── HeadlineSimilarityService   [Stage 4] Jaccard 유사도 계산
    ├── TopicClusteringService      [Stage 4] Union-Find 클러스터링, Topic 생성
    ├── PromptBuilder               [Stage 5] Gemini 프롬프트 구성
    ├── AiSummaryService            [Stage 5] 토픽 단건·일괄 AI 요약
    ├── TopicService                토픽 CRUD + 비교 조회
    ├── NewsIngestionPipelineService 전체 파이프라인 오케스트레이션
    ├── ArticleService              기사 조회 (레거시 수집 메서드는 @Deprecated)
    ├── PublisherService            언론사 CRUD
    └── AuthService                 관리자 JWT 로그인
```

---

## API 목록

### Pipeline (통합 실행)
| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/pipeline/collect` | 전체 파이프라인 실행 (수집→저장→클러스터링→AI요약) |

### Dashboard (파이프라인 모니터링)
| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/dashboard/summary` | 전체 기사 수, 토픽 수, 오늘 수집 기사 수, 실패 작업 수, 마지막 수집 시간, 최근 실행 이력 |
| GET | `/api/dashboard/charts` | 언론사별 기사 수, 국가별 토픽 수, 파이프라인 성공/실패 건수 |
| GET | `/api/pipeline/stream` | RSS 수집 → 기사 저장 → 토픽 클러스터링 → AI 요약 → 완료 상태를 SSE로 스트리밍 |

대시보드 관측 데이터는 뉴스 도메인 테이블과 분리합니다.

```text
pipeline_run
- 파이프라인 실행 1건의 최종 상태, 집계 수치, 시작/종료 시간

pipeline_step_history
- 실행별 단계 이력
- step, status, targetName, processedCount, errorType, errorMessage 저장
- 예: RSS_COLLECT / FAILED / Fox News / RSS_TIMEOUT
```

### Topic (핵심 기능)
| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/topics` | 토픽 목록 조회 (status 필터, 페이지네이션) |
| GET | `/api/v1/topics/{id}` | 토픽 상세 + 성향/국가 분포 |
| GET | `/api/v1/topics/{id}/articles` | 언론사 비교 조회 (AI 브리핑 포함, groupBy 지원) |
| GET | `/api/v1/topics/{id}/comparison` | 위와 동일 (별칭) |
| POST | `/api/v1/topics` | 토픽 수동 생성 |
| PUT | `/api/v1/topics/{id}` | 토픽 수정 |
| DELETE | `/api/v1/topics/{id}` | 토픽 삭제 |
| POST | `/api/v1/topics/cluster` | 기사 자동 클러스터링 |
| POST | `/api/v1/topics/{id}/summarize` | 단건 AI 요약 생성 |
| POST | `/api/v1/topics/summarize` | 미요약 토픽 일괄 AI 요약 |
| POST | `/api/v1/topics/{id}/articles` | 토픽에 기사 연결 |
| DELETE | `/api/v1/topics/{id}/articles/{articleId}` | 토픽-기사 연결 해제 |

### Collect (단계별 실행)
| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/collect/preview` | RSS 수집 미리보기 (DB 저장 없음) |
| POST | `/api/v1/collect/fetch-and-save` | RSS 수집 + 정규화 + 저장 |

### Article / Publisher / Auth
| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/articles` | 기사 목록 조회 |
| GET | `/api/v1/articles/{id}` | 기사 단건 조회 |
| GET/POST/PUT/DELETE | `/api/v1/publishers` | 언론사 CRUD |
| POST | `/api/v1/auth/login` | 관리자 JWT 로그인 |

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

---

## 설계 결정

### 중복 제거 3단계 우선순위
RSS 피드마다 기사 식별 방식이 달라 단일 기준으로는 중복을 잡기 어렵습니다.

```
Priority 1 — rssGuid + publisherId   RSS 표준 <guid>. 가장 신뢰도 높음.
Priority 2 — normalizedUrl           추적 파라미터 제거 후 URL 비교.
Priority 3 — dedupeKey               SHA-256(publisherName|normalizedHeadline|date). URL 변경 시 fallback.
```

### Topic 클러스터링 (NLP 없이)
```
헤드라인 정규화 → 불용어 제거 → Jaccard 유사도 계산 → Union-Find 그룹화
임계값(0.25) 이상이면 동일 이슈로 판단. 2개 이상 언론사 묶여야 Topic 생성.
```

외부 NLP 의존 없이 설명 가능한 알고리즘만으로 구현. 정확도보다 투명성 우선.

### AI 요약 원칙
- 기사 본문 저장·재배포 없음
- 헤드라인 + description(최대 120자) + publisherName만 프롬프트에 포함
- 팩트 중심 1~2문장, 성향 분석 없음

---

## 로컬 실행

```bash
# 필수 환경변수
export GEMINI_API_KEY=your-key
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=your-password
export JWT_SECRET=your-secret-at-least-32-chars

# 실행
./gradlew bootRun

# 테스트
./gradlew test
```

### 데모 대시보드 실행

H2 인메모리 DB를 유지하면서 `demo` profile을 켜면 대시보드용 샘플 기사, 토픽, 파이프라인 이력이 자동 생성됩니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=demo'
```

데모 관리자 계정:

```text
username: admin
password: admin1234
```

H2 Console: `http://localhost:8080/h2-console`  
JDBC URL: `jdbc:h2:mem:newsdb`
