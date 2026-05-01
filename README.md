# Cross Check News — Backend API

동일한 이슈를 서로 다른 나라·성향의 언론사가 어떻게 다르게 보도하는지 비교하는 뉴스 관점 비교 서비스의 백엔드 API입니다.

포트폴리오 목적으로 제작되었으며, 복잡한 인프라보다 **아키텍처 설계와 데이터 흐름의 명확성**에 초점을 맞췄습니다.

---

## 빠른 시작

```bash
./gradlew bootRun
```

실행하면 **데모 데이터가 자동 적재**되어 별도 설정 없이 API와 대시보드를 바로 확인할 수 있습니다.

| 주소 | 설명 |
|------|------|
| `http://localhost:8080/swagger-ui/index.html` | Swagger UI (전체 API 문서) |
| `http://localhost:8080/h2-console` | H2 콘솔 (JDBC URL: `jdbc:h2:mem:newsdb`) |

관리자 계정: `admin` / `admin1234`

---

## 로컬 실행 설정

본 프로젝트는 과제 리뷰 편의를 위해 H2, 관리자 계정, JWT secret 등 로컬 실행용 기본 설정값을 포함하고 있습니다.

외부 AI API key는 제출 환경에서 필수로 사용하지 않으며, 데모 실행은 저장된 시연용 데이터를 기준으로 확인할 수 있습니다. 운영 환경에서는 API key, JWT secret, DB 접속 정보 등을 환경 변수 또는 별도 secret 관리 방식으로 분리하는 것을 전제로 합니다.

## 핵심 기능 — 5단계 뉴스 파이프라인

> **이 프로젝트의 핵심은 단일 엔드포인트 `POST /api/v1/pipeline/collect` 입니다.**
> 한 번의 요청으로 RSS 수집, 정규화, 중복 제거, 토픽 클러스터링까지 실행되며, AI 요약 단계는 파이프라인 구조에 포함되어 있습니다.

```
[1] RSS 수집       FeedSource(enum) → RssCollectService → ArticleCandidate
[2] 정규화         URL 정규화, HTML 태그 제거, 헤드라인 소문자화
[3] 중복 제거      rssGuid → normalizedUrl → dedupeKey(SHA-256) 3단계 우선순위
[4] 클러스터링     TF-IDF + 코사인 유사도 → 다수 언론사 묶음 → Topic 자동 생성
[5] AI 요약       파이프라인 단계로 포함. 데모 실행에서는 외부 AI 호출 없이 저장된 요약 데이터 사용
```

### 수집 대상 언론사

| 언론사 | 국가 | 성향 |
|--------|------|------|
| Fox News | US | Conservative |
| New York Times | US | Progressive |
| 조선일보 | KR | Conservative |
| 한겨레 | KR | Progressive |

---

## 데모 데이터

애플리케이션 시작 시 `DemoDataInitializer`가 자동으로 이전 날짜의 뉴스 데이터를 H2에 적재합니다.

- **적재 기간**: 2026-04-28 ~ 2026-04-30 (3일치 과거 데이터)
- **데이터 위치**: `src/main/resources/demo-data/` (날짜별 JSON 파일)
- **포함 내용**: 기사·토픽·파이프라인 실행 이력

실행 직후 대시보드 API(`/api/dashboard/summary`)로 시연 데이터 기반의 집계 결과를 확인할 수 있습니다.

---

## 대시보드 모니터링

파이프라인 실행 이력을 뉴스 도메인 테이블과 분리하여 별도 관측 테이블에 저장합니다.

```
pipeline_run          — 파이프라인 실행 단위 (상태, 집계 수치, 시작/종료 시간)
pipeline_step_history — 단계별 상세 이력 (step, status, targetName, errorType, errorMessage)
```

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/dashboard/summary` | 기사 수, 토픽 수, 성공/실패 건수, 최근 실행 이력 |
| GET | `/api/dashboard/charts` | 언론사별 기사 수, 국가별 토픽 수, 파이프라인 상태 분포 |
| GET | `/api/v1/pipeline/histories` | 단계별 실행 이력 (날짜·상태 필터, 페이지네이션) |

---

## 전체 API 목록

### Pipeline
| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/pipeline/collect` | 파이프라인 실행 (수집 → 저장 → 클러스터링, AI 요약 단계는 구조상 포함) |

### Topic
| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/topics` | 토픽 목록 (status·날짜 필터, 페이지네이션) |
| GET | `/api/v1/topics/{id}` | 토픽 상세 + 연결 기사 + 성향/국가 분포 |
| POST | `/api/v1/topics/cluster` | 기사 자동 클러스터링 |
| POST | `/api/v1/topics/summarize` | 미요약 토픽 일괄 AI 요약 |

### Article / Publisher
| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/articles` | 기사 목록 (헤드라인 검색·날짜 필터, 페이지네이션) |
| GET | `/api/v1/articles/{id}` | 기사 단건 조회 |
| GET | `/api/v1/publishers` | 언론사 목록 (페이지네이션) |

### Auth
| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/auth/login` | 관리자 JWT 로그인 |

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.x |
| ORM | Spring Data JPA |
| Database | H2 (In-Memory) |
| RSS Parser | Rome 2.1 |
| AI | Gemini API 연동 코드 / Ollama fallback 고려 구조 | 
| Auth | JWT (JJWT 0.12) |
| Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Gradle |

---

## 설계 결정

### 중복 제거 3단계 우선순위

RSS 피드마다 기사 식별 방식이 달라 단일 기준으로는 중복을 잡기 어렵습니다.

```
Priority 1 — rssGuid + publisherId    RSS 표준 <guid>. 가장 신뢰도 높음.
Priority 2 — normalizedUrl            추적 파라미터 제거 후 URL 비교.
Priority 3 — dedupeKey                SHA-256(publisherName|normalizedHeadline|date). URL 변경 시 fallback.
```

### Topic 클러스터링

외부 NLP 라이브러리 없이 설명 가능한 알고리즘만으로 구현했습니다.

```
헤드라인 정규화 → 불용어 제거 → TF-IDF 벡터화 → 코사인 유사도 → 임계값(0.3) 이상 그룹화
2개 이상 언론사 기사가 묶여야 Topic 생성.
```

### AI 요약

- AI 요약 단계는 파이프라인 설계에 포함되어 있으며, `Topic.aiSummary`를 생성하는 구조로 구현했습니다.
- 다만 외부 AI API 사용량 제한과 제출 환경의 실행 안정성을 고려해, 데모 실행에서는 AI 호출이 필수로 동작하지 않도록 제한했습니다.
- 데모 데이터에는 요약 결과를 포함해, 리뷰어가 대시보드와 토픽 상세 화면에서 요약 표시 흐름을 확인할 수 있도록 구성했습니다.
- AI 연동부는 Gemini API와 로컬 Ollama fallback을 고려해 분리했습니다.

---

## 테스트

```bash
./gradlew test
```
