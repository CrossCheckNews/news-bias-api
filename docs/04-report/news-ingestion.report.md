# News Ingestion — PDCA Completion Report

> Feature: news-ingestion
> Project: news-bias-api (Spring Boot 3.5.x / Java 21)
> Completed: 2026-04-05
> Match Rate: 93%

---

## 1. Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | 언론사별 뉴스 헤드라인 데이터가 없어 편향 비교 서비스를 시작할 수 없었음 |
| **Solution** | Publisher CRUD API + RSS 기반 Article 수집 API + Topic 비교 뷰 API 구현 |
| **Function/UX Effect** | `/api/v1/publishers`, `/api/v1/articles/fetch`, `/api/v1/topics/{id}/articles` 등 REST API 완성. 헤드라인만 저장해 법적 리스크 최소화 |
| **Core Value** | 편향 비교를 위한 데이터 레이어 완성. 국가·정치성향별 헤드라인 비교 API까지 구현해 핵심 서비스 기능 조기 달성 |

### 1.1 Value Delivered

| 지표 | 계획 | 실제 |
|------|------|------|
| 구현 파일 수 | ~13개 | 32개 (Topic 기능 포함) |
| API 엔드포인트 | 9개 | 17개 |
| 단위 테스트 | 2개 클래스 | 2개 클래스, 13개 케이스 통과 |
| Match Rate | ≥90% | 93% |
| 보안 이슈 | 0건 | 1건 발견 → 해결 (API 키 노출) |

---

## 2. Plan Success Criteria — Final Status

| # | 기준 | 상태 | 근거 |
|---|------|:----:|------|
| 1 | Publisher CRUD API 동작 | ✅ Met | 5개 엔드포인트 `/api/v1/publishers` |
| 2 | Article 수집 API 동작 | ✅ Met | `POST /api/v1/articles/fetch` + Rome RSS 파싱 |
| 3 | 중복 URL 처리 | ✅ Met | `existsByUrl()` skip + DB unique constraint |
| 4 | 단위 테스트 통과 | ✅ Met | 13/13 PASSED (failures=0, errors=0) |
| 5 | H2 콘솔 데이터 확인 | ✅ Met | `/h2-console` 활성화, create-drop DDL |

**Overall: 5/5 (100%)**

---

## 3. Key Decisions & Outcomes

| 결정 | 선택 | 결과 |
|------|------|------|
| **Architecture** | Option C — Pragmatic Layered | Controller→Service→Repository 계층 명확, 과도한 추상화 없음 |
| **RSS 파싱** | Rome 라이브러리 | RSS/Atom 양식 모두 지원, 실제 BBC/Reuters 피드 파싱 검증 가능 |
| **중복 처리** | existsByUrl() 사전 체크 | DataIntegrityViolation 대신 명시적 skip 카운트 반환 |
| **country 타입** | String → Country enum으로 변경 | 잘못된 국가 코드 입력 방지, API 응답 일관성 향상 |
| **스코프 확장** | Topic 기능 동시 구현 | 비교 뷰 API(`/topics/{id}/articles?groupBy=leaning`)까지 완성 |
| **API 키 보안** | 환경변수 `${GEMINI_API_KEY}` | 하드코딩 → 환경변수 분리로 보안 이슈 해결 |

---

## 4. 구현 현황

### 4.1 구현된 API

**Publishers** `/api/v1/publishers`
- POST / GET / GET `/{id}` / PUT `/{id}` / DELETE `/{id}`

**Articles** `/api/v1/articles`
- POST `/fetch` (RSS 수집) / POST (수동 등록) / GET (목록) / GET `/{id}`

**Topics** `/api/v1/topics` *(스코프 외 추가 구현)*
- POST / GET / GET `/{id}` / PUT `/{id}` / DELETE `/{id}`
- GET `/{id}/articles` (비교 뷰, groupBy=leaning|country)
- POST `/{id}/articles` / DELETE `/{id}/articles/{articleId}`

### 4.2 테스트 결과

```
PublisherServiceTest  7/7  PASSED
ArticleServiceTest    6/6  PASSED
─────────────────────────────────
Total                13/13 PASSED  failures=0  errors=0
```

---

## 5. 잔여 이슈 (다음 사이클)

| 항목 | 내용 | 우선순위 |
|------|------|:-------:|
| 설계서 업데이트 | Country enum, `/api/v1` prefix, ArticleResponse 필드 변경 미반영 | Low |
| Topic 설계 문서 | Topic 기능 구현됐으나 Plan/Design 문서 없음 | Medium |
| 통합 테스트 | Controller 레이어 `@SpringBootTest` 기반 API 테스트 부재 | Medium |
| Gemini AI 연동 | `gemini.api.key` 설정됐으나 실제 연동 미구현 | Low |

---

## 6. 회고

**잘 된 점**
- Design Ref 주석(`// Design Ref: §N`) 덕분에 설계-코드 추적이 용이했음
- Country enum 도입이 설계 이탈이었으나 결과적으로 더 나은 선택
- Topic 기능을 조기 구현해 핵심 비교 뷰 API까지 완성

**개선점**
- 스코프 확장(Topic) 시 설계 문서를 먼저 작성했어야 함
- API 키를 처음부터 환경변수로 관리했어야 함 (Check 단계에서 발견)
