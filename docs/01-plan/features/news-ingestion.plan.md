# News Ingestion Plan

> Feature: news-ingestion
> Project: news-bias-api (Spring Boot 3.5.x / Java 21)
> Date: 2026-04-04

---

## Executive Summary

| Item | Value |
|------|-------|
| **Feature** | News Ingestion — Publisher & Article Collection API |
| **Plan Date** | 2026-04-04 |
| **Estimated Effort** | ~4–6 hours |
| **Files to Create** | ~10 files |
| **Lines Changed** | ~350–450 lines |

### Value Delivered (4-Perspective)

| Perspective | Content |
|-------------|---------|
| **Problem** | 언론사별 뉴스 헤드라인 데이터가 없어서 편향 비교 서비스를 시작할 수 없음 |
| **Solution** | Publisher(언론사) 관리 API + Article 수집 API + RSS 기반 외부 뉴스 fetch 기능 제공 |
| **Function/UX Effect** | `GET /publishers`, `POST /articles/fetch` 등 REST API로 언론사 등록 및 뉴스 수집 가능. 헤드라인만 저장하여 법적 리스크 최소화 |
| **Core Value** | 편향 비교를 위한 최소 데이터 레이어 확보. 이후 토픽 클러스터링과 AI 요약의 기반이 됨 |

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

## 1. User Intent Discovery

### Core Problem
여러 언론사가 동일한 이슈를 어떻게 다르게 보도하는지 비교하려면,
먼저 각 언론사의 뉴스 헤드라인과 링크를 수집해야 한다.
현재 데이터 수집 레이어가 전무하므로 뉴스 편향 비교 기능을 시작할 수 없다.

### Target Users
- **Primary**: 포트폴리오 검토자 — 백엔드 설계 역량 평가
- **Secondary**: 서비스 운영자 — 언론사 등록 및 뉴스 수집 관리

### Success Criteria

| # | Criteria | Measurement |
|---|---------|-------------|
| 1 | Publisher CRUD API 동작 | `POST /publishers` → 201, `GET /publishers` → 200 + 목록 |
| 2 | Article 수집 API 동작 | `POST /articles/fetch?publisherId={id}` → RSS 파싱 후 DB 저장 |
| 3 | 중복 URL 처리 | 동일 URL 재수집 시 400 또는 idempotent upsert |
| 4 | 단위 테스트 통과 | Service 레이어 기본 테스트 |
| 5 | H2 콘솔 데이터 확인 | `/h2-console` 에서 PUBLISHER, ARTICLE 테이블 확인 |

### Constraints
- 기사 본문은 저장하지 않음 (헤드라인 + URL만)
- H2 in-memory DB (개발용)
- 스케줄러 없음 — 수동 API 트리거
- 포트폴리오 목적 — 과도한 추상화 지양

---

## 2. Requirements

### Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-01 | Publisher 등록 (이름, 국가, 정치성향, RSS URL) | Must |
| FR-02 | Publisher 목록 조회 / 단건 조회 | Must |
| FR-03 | Publisher 수정 / 삭제 | Should |
| FR-04 | RSS URL로 뉴스 헤드라인 + 링크 fetch | Must |
| FR-05 | fetch된 Article DB 저장 (publisherId 연결) | Must |
| FR-06 | Article 목록 조회 (publisherId 필터, 페이지네이션) | Must |
| FR-07 | 중복 URL 처리 (unique constraint + 409 응답) | Must |
| FR-08 | Article 수동 등록 (POST /articles) | Should |

### Non-Functional Requirements

| ID | Requirement |
|----|-------------|
| NFR-01 | REST 표준 응답 구조 (data / error 일관성) |
| NFR-02 | @Valid 입력 검증 |
| NFR-03 | H2 콘솔 활성화 (개발 환경) |
| NFR-04 | Lombok @Builder / @Getter 활용 |

---

## 3. Domain Model

### Publisher

| Field | Type | Description |
|-------|------|-------------|
| id | Long (PK) | Auto increment |
| name | String (unique) | 언론사명 (e.g., "BBC News") |
| country | String | 국가 코드 (e.g., "UK", "US") |
| politicalLeaning | Enum | LEFT / CENTER / RIGHT |
| rssUrl | String | RSS 피드 URL |
| createdAt | LocalDateTime | 생성일시 |

### Article

| Field | Type | Description |
|-------|------|-------------|
| id | Long (PK) | Auto increment |
| headline | String | 뉴스 헤드라인 |
| url | String (unique) | 기사 링크 (중복 방지) |
| publishedAt | LocalDateTime | 기사 발행일 |
| publisher | Publisher (ManyToOne) | 언론사 참조 |
| fetchedAt | LocalDateTime | 수집일시 |

### Political Leaning Enum
```
LEFT, CENTER, RIGHT
```

---

## 4. API Design

### Publisher API

| Method | Path | Description | Status |
|--------|------|-------------|--------|
| POST | /publishers | 언론사 등록 | 201 |
| GET | /publishers | 전체 목록 | 200 |
| GET | /publishers/{id} | 단건 조회 | 200 / 404 |
| PUT | /publishers/{id} | 수정 | 200 |
| DELETE | /publishers/{id} | 삭제 | 204 |

### Article API

| Method | Path | Description | Status |
|--------|------|-------------|--------|
| POST | /articles/fetch | RSS fetch → DB 저장 | 200 |
| POST | /articles | 수동 등록 | 201 |
| GET | /articles | 목록 (publisherId 필터, 페이지) | 200 |
| GET | /articles/{id} | 단건 조회 | 200 / 404 |

**POST /articles/fetch Request Body:**
```json
{
  "publisherId": 1
}
```

**GET /articles Query Params:**
```
?publisherId=1&page=0&size=20
```

---

## 5. Package Structure

```
com.crosschecknews.api
├── controller/
│   ├── PublisherController.java
│   └── ArticleController.java
├── service/
│   ├── PublisherService.java
│   ├── ArticleService.java
│   └── RssFetchService.java
├── repository/
│   ├── PublisherRepository.java
│   └── ArticleRepository.java
├── domain/
│   ├── Publisher.java          (@Entity)
│   ├── Article.java            (@Entity)
│   └── PoliticalLeaning.java   (Enum)
└── dto/
    ├── PublisherRequest.java
    ├── PublisherResponse.java
    ├── ArticleRequest.java
    ├── ArticleFetchRequest.java
    └── ArticleResponse.java
```

---

## 6. Dependencies

추가 dependency 필요:

```gradle
// RSS 파싱 (Rome)
implementation 'com.rometools:rome:2.1.0'
```

> Rome 라이브러리: Java 표준 RSS/Atom 파싱 라이브러리

---

## 7. Risk Analysis

| Risk | Probability | Impact | Mitigation |
|------|:-----------:|:------:|-----------|
| RSS URL 파싱 실패 (구조 변경) | Medium | High | try-catch + 에러 응답 반환, URL 유효성 검증 |
| URL 중복 저장 | High | Medium | DB unique constraint + @Column(unique=true) |
| 정치성향 분류 주관성 | Low | Low | Enum 제공 + 운영자 직접 입력 방식 |
| H2 재시작 시 데이터 소실 | High | Low | 개발용 의도된 동작, 문서화 |

---

## 8. Implementation Order

1. **Domain** — Publisher, Article entities + PoliticalLeaning enum
2. **Repository** — PublisherRepository, ArticleRepository
3. **DTO** — Request/Response DTOs
4. **Service** — PublisherService, ArticleService
5. **RssFetchService** — Rome 라이브러리로 RSS 파싱
6. **Controller** — PublisherController, ArticleController
7. **Config** — application.properties (H2 콘솔, JPA 설정)
8. **Test** — PublisherService, ArticleService 단위 테스트

---

## 9. Out of Scope

- 뉴스 본문 저장
- 토픽 클러스터링 (별도 기능)
- AI 요약 생성 (별도 기능)
- 인증/인가
- 스케줄러 (자동 수집)
- 프로덕션 DB (PostgreSQL 등)
