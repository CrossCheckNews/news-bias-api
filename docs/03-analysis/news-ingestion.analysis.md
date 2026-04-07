# News Ingestion — Gap Analysis

> Feature: news-ingestion
> Phase: Check
> Date: 2026-04-05
> Match Rate: 93%

---

## Context Anchor

| Key | Value |
|-----|-------|
| **WHY** | 뉴스 편향 비교 서비스의 핵심 데이터 수집 레이어. 이 없이는 이후 모든 기능이 불가 |
| **WHO** | 포트폴리오 검토자(백엔드 아키텍처 평가), 서비스 운영자(언론사 추가 및 수집 트리거) |
| **RISK** | RSS 구조 변경 시 파싱 실패 / URL 중복 저장 / 언론사 정치성향 분류의 주관성 |
| **SUCCESS** | Publisher CRUD + Article 수집 API 정상 동작 + H2 저장 확인 + 단위 테스트 통과 |
| **SCOPE** | 헤드라인 + 링크만 저장 (본문 없음). 수동 트리거만 (스케줄러 없음). H2 in-memory DB |

---

## 1. Plan Success Criteria

| # | 기준 | 상태 | 근거 |
|---|------|:----:|------|
| 1 | Publisher CRUD API 동작 | ✅ Met | PublisherController 5개 엔드포인트 구현 |
| 2 | Article 수집 API 동작 | ✅ Met | POST /api/v1/articles/fetch + RssFetchService(Rome) |
| 3 | 중복 URL 처리 | ✅ Met | existsByUrl() 체크 + unique constraint |
| 4 | 단위 테스트 통과 | ✅ Met | PublisherServiceTest(7) + ArticleServiceTest(6) = 13/13 PASS |
| 5 | H2 콘솔 데이터 확인 | ✅ Met | application.properties H2 콘솔 활성화 완료 |

**Success Rate: 5/5 (100%)**

---

## 2. Structural Match

| 항목 | 설계 | 구현 | 상태 |
|------|------|------|:----:|
| domain/Publisher.java | ✅ | ✅ | 일치 |
| domain/Article.java | ✅ | ✅ | 일치 |
| domain/PoliticalLeaning.java | ✅ | ✅ | 일치 |
| repository/PublisherRepository.java | ✅ | ✅ | 일치 |
| repository/ArticleRepository.java | ✅ | ✅ | 일치 |
| dto/ (5개) | ✅ | ✅ | 일치 |
| exception/ (4개) | ✅ | ✅ | 일치 |
| service/ (3개) | ✅ | ✅ | 일치 |
| controller/ (2개) | ✅ | ✅ | 일치 |
| test/service/ (2개) | ✅ | ✅ | 구현 완료 |

**Structural Score: 100%**

---

## 3. Design Deviations

| 항목 | 설계 | 구현 | 판정 |
|------|------|------|:----:|
| Publisher.country 타입 | String | Country enum | ⚠️ 개선 (긍정적 이탈) |
| ArticleResponse 필드 | publisherId, publisherName | + country, leaning 추가 | ⚠️ 개선 (비교 기능 목적) |
| API 기본 경로 | /publishers | /api/v1/publishers | ⚠️ 설계서 미반영 |
| 스코프 외 구현 | news-ingestion 범위 | Topic 관련 전체 추가 구현 | ℹ️ 의도적 확장 |

---

## 4. Critical Issues — 해결 완료

| 이슈 | 해결 |
|------|------|
| API 키 하드코딩 (gemini.api.key) | `${GEMINI_API_KEY}` 환경변수로 교체 |
| 단위 테스트 미구현 | PublisherServiceTest, ArticleServiceTest 작성 및 통과 |

---

## 5. Match Rate

```
Structural:  100%
Functional:   90%
Contract:     85%  (API prefix + response 필드 변경)
─────────────────────────────────────
Overall:      93%  (목표 90% 달성)
```
