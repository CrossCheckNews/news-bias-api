# News Ingestion Design

> Feature: news-ingestion
> Architecture: Option C — Pragmatic Layered
> Date: 2026-04-04

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

## 1. Architecture Overview

**선택**: Option C — Pragmatic Layered Architecture

```
┌─────────────────────────────────────────────────┐
│                  HTTP Request                   │
└───────────────────────┬─────────────────────────┘
                        ▼
┌─────────────────────────────────────────────────┐
│              Controller Layer                   │
│   PublisherController  │  ArticleController     │
└───────────────────────┬─────────────────────────┘
                        ▼
┌─────────────────────────────────────────────────┐
│               Service Layer                     │
│  PublisherService │ ArticleService │ RssFetch   │
└───────────────────┬─────────────────────────────┘
                    ▼
┌─────────────────────────────────────────────────┐
│             Repository Layer                    │
│    PublisherRepository │ ArticleRepository      │
└───────────────────┬─────────────────────────────┘
                    ▼
┌─────────────────────────────────────────────────┐
│              Domain (Entity)                    │
│         Publisher  │  Article                   │
└─────────────────────────────────────────────────┘
                    ▼
┌─────────────────────────────────────────────────┐
│             H2 In-Memory Database               │
└─────────────────────────────────────────────────┘
```

**외부 연동**:
```
ArticleService → RssFetchService → com.rometools:rome → RSS/Atom Feed URL
```

---

## 2. Package Structure

```
src/main/java/com/crosschecknews/api/
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
│   ├── Publisher.java
│   ├── Article.java
│   └── PoliticalLeaning.java   (enum)
└── dto/
    ├── PublisherRequest.java
    ├── PublisherResponse.java
    ├── ArticleFetchRequest.java
    ├── ArticleRequest.java
    └── ArticleResponse.java

src/test/java/com/crosschecknews/api/
├── service/
│   ├── PublisherServiceTest.java
│   └── ArticleServiceTest.java
└── NewsBiasApiApplicationTests.java (existing)

src/main/resources/
└── application.properties          (update)
```

**총 신규 파일**: 13개 (기존 application.properties 수정 포함)

---

## 3. Domain Design

### 3.1 Publisher Entity

```java
@Entity
@Table(name = "publisher")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Publisher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PoliticalLeaning politicalLeaning;

    @Column(nullable = false)
    private String rssUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

### 3.2 Article Entity

```java
@Entity
@Table(name = "article",
       uniqueConstraints = @UniqueConstraint(columnNames = "url"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String headline;

    @Column(nullable = false, unique = true)
    private String url;

    private LocalDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id", nullable = false)
    private Publisher publisher;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fetchedAt;

    @PrePersist
    protected void onFetch() {
        this.fetchedAt = LocalDateTime.now();
    }
}
```

### 3.3 PoliticalLeaning Enum

```java
public enum PoliticalLeaning {
    LEFT, CENTER, RIGHT
}
```

---

## 4. API Contract

### 4.1 Publisher API

#### POST /publishers
- **Request**: `PublisherRequest` (`name`, `country`, `politicalLeaning`, `rssUrl`)
- **Response**: `PublisherResponse` (201 Created)
- **Validation**: `@NotBlank` on all fields, `@NotNull` on enum

#### GET /publishers
- **Response**: `List<PublisherResponse>` (200 OK)

#### GET /publishers/{id}
- **Response**: `PublisherResponse` (200 OK) / `404` if not found

#### PUT /publishers/{id}
- **Request**: `PublisherRequest`
- **Response**: `PublisherResponse` (200 OK) / `404`

#### DELETE /publishers/{id}
- **Response**: 204 No Content / `404`

---

### 4.2 Article API

#### POST /articles/fetch
- **Request**: `ArticleFetchRequest` (`publisherId: Long`)
- **Logic**: Publisher 조회 → rssUrl로 Rome 파싱 → 각 entry를 Article로 저장 (중복 skip)
- **Response**: `{ "fetched": N, "skipped": M }` (200 OK)
- **Error**: Publisher 없으면 404, RSS 파싱 실패 시 502

#### POST /articles
- **Request**: `ArticleRequest` (`headline`, `url`, `publishedAt`, `publisherId`)
- **Response**: `ArticleResponse` (201 Created)
- **Error**: 중복 URL → 409 Conflict

#### GET /articles
- **Query**: `?publisherId=1&page=0&size=20`
- **Response**: `Page<ArticleResponse>` (200 OK)

#### GET /articles/{id}
- **Response**: `ArticleResponse` (200 OK) / `404`

---

## 5. DTO Design

### PublisherRequest
```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PublisherRequest {
    @NotBlank private String name;
    @NotBlank private String country;
    @NotNull  private PoliticalLeaning politicalLeaning;
    @NotBlank private String rssUrl;
}
```

### PublisherResponse
```java
@Getter
@Builder
public class PublisherResponse {
    private Long id;
    private String name;
    private String country;
    private PoliticalLeaning politicalLeaning;
    private String rssUrl;
    private LocalDateTime createdAt;

    public static PublisherResponse from(Publisher p) { ... }
}
```

### ArticleFetchRequest
```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleFetchRequest {
    @NotNull private Long publisherId;
}
```

### ArticleRequest
```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleRequest {
    @NotBlank private String headline;
    @NotBlank private String url;
    private LocalDateTime publishedAt;
    @NotNull private Long publisherId;
}
```

### ArticleResponse
```java
@Getter
@Builder
public class ArticleResponse {
    private Long id;
    private String headline;
    private String url;
    private LocalDateTime publishedAt;
    private LocalDateTime fetchedAt;
    private Long publisherId;
    private String publisherName;

    public static ArticleResponse from(Article a) { ... }
}
```

---

## 6. Service Design

### 6.1 PublisherService

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublisherService {
    private final PublisherRepository publisherRepository;

    @Transactional
    public PublisherResponse create(PublisherRequest request) { ... }

    public List<PublisherResponse> findAll() { ... }

    public PublisherResponse findById(Long id) { ... }  // throws ResourceNotFoundException

    @Transactional
    public PublisherResponse update(Long id, PublisherRequest request) { ... }

    @Transactional
    public void delete(Long id) { ... }
}
```

### 6.2 ArticleService

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleService {
    private final ArticleRepository articleRepository;
    private final PublisherRepository publisherRepository;
    private final RssFetchService rssFetchService;

    @Transactional
    public FetchResult fetchFromRss(Long publisherId) { ... }
    // - Publisher 조회 (없으면 404)
    // - rssFetchService.fetch(rssUrl) 호출
    // - 중복 URL skip, 신규만 저장
    // - { fetched: N, skipped: M } 반환

    @Transactional
    public ArticleResponse create(ArticleRequest request) { ... }
    // - 중복 URL → DataIntegrityViolationException → 409

    public Page<ArticleResponse> findAll(Long publisherId, Pageable pageable) { ... }

    public ArticleResponse findById(Long id) { ... }
}
```

### 6.3 RssFetchService

```java
@Service
public class RssFetchService {
    public List<RssEntry> fetch(String rssUrl) {
        // Rome SyndFeedInput으로 RSS 파싱
        // SyndEntry → RssEntry(title, link, publishedDate) 변환
        // 파싱 실패 시 RssFetchException throw
    }
}

// 내부 DTO
record RssEntry(String title, String link, LocalDateTime publishedAt) {}
```

---

## 7. Repository Design

### PublisherRepository
```java
public interface PublisherRepository extends JpaRepository<Publisher, Long> {
    boolean existsByName(String name);
}
```

### ArticleRepository
```java
public interface ArticleRepository extends JpaRepository<Article, Long> {
    Page<Article> findByPublisherId(Long publisherId, Pageable pageable);
    boolean existsByUrl(String url);
}
```

---

## 8. Exception Handling

### Custom Exceptions

```java
// 404 용
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
}

// RSS 파싱 실패 (502 Bad Gateway)
public class RssFetchException extends RuntimeException {
    public RssFetchException(String url, Throwable cause) { ... }
}
```

### Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle404(...) { ... }  // 404

    @ExceptionHandler(RssFetchException.class)
    public ResponseEntity<ErrorResponse> handle502(...) { ... }  // 502

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handle409(...) { ... }  // 409 (중복 URL)

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle400(...) { ... }  // 400 (validation)
}
```

### ErrorResponse DTO
```java
@Getter
@Builder
public class ErrorResponse {
    private int status;
    private String message;
    private LocalDateTime timestamp;
}
```

---

## 9. Configuration

### application.properties

```properties
spring.application.name=news-bias-api

# H2 In-Memory Database
spring.datasource.url=jdbc:h2:mem:newsdb;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# H2 Console (개발용)
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# JPA
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false
```

### build.gradle 추가

```gradle
implementation 'com.rometools:rome:2.1.0'
```

---

## 10. Test Design

### PublisherServiceTest

```java
@ExtendWith(MockitoExtension.class)
class PublisherServiceTest {
    @Mock private PublisherRepository publisherRepository;
    @InjectMocks private PublisherService publisherService;

    @Test void 언론사_등록_성공() { ... }
    @Test void 중복_언론사명_등록_실패() { ... }
    @Test void 존재하지_않는_언론사_조회_실패() { ... }
}
```

### ArticleServiceTest

```java
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {
    @Mock private ArticleRepository articleRepository;
    @Mock private PublisherRepository publisherRepository;
    @Mock private RssFetchService rssFetchService;
    @InjectMocks private ArticleService articleService;

    @Test void RSS_수집_성공() { ... }
    @Test void 존재하지_않는_Publisher_수집_실패() { ... }
    @Test void 중복_URL_skip() { ... }
}
```

---

## 11. Implementation Guide

### 11.1 Implementation Order

1. `application.properties` 업데이트 (H2, JPA 설정)
2. `build.gradle` — rome 의존성 추가
3. `domain/` — `PoliticalLeaning`, `Publisher`, `Article`
4. `repository/` — `PublisherRepository`, `ArticleRepository`
5. `dto/` — 5개 DTO 클래스
6. `exception/` — `ResourceNotFoundException`, `RssFetchException`, `ErrorResponse`, `GlobalExceptionHandler`
7. `service/RssFetchService` — Rome 파싱 로직
8. `service/PublisherService` — CRUD
9. `service/ArticleService` — fetch + CRUD
10. `controller/PublisherController`
11. `controller/ArticleController`
12. `test/service/` — 단위 테스트 2개

### 11.2 Key Implementation Notes

- `@Transactional(readOnly = true)` — Service 기본값, 쓰기 메서드만 `@Transactional`
- Rome 파싱: `SyndFeedInput` + `XmlReader` 사용, UTF-8 인코딩 주의
- `DataIntegrityViolationException` → 409: Spring JPA가 unique constraint 위반 시 자동 throw
- `spring.jpa.open-in-view=false` — Lazy loading 이슈 방지, DTO 변환을 Service에서 처리

### 11.3 Session Guide (Module Map)

| Module | Files | Effort |
|--------|-------|--------|
| M1 — Foundation | application.properties, build.gradle, domain/*, repository/* | ~45분 |
| M2 — DTO & Exception | dto/*(5), exception/*(4) | ~30분 |
| M3 — Service | RssFetchService, PublisherService, ArticleService | ~60분 |
| M4 — Controller | PublisherController, ArticleController | ~30분 |
| M5 — Test | PublisherServiceTest, ArticleServiceTest | ~45분 |

**추천 세션 분할**:
- Session 1: `/pdca do news-ingestion --scope M1,M2,M3`
- Session 2: `/pdca do news-ingestion --scope M4,M5`
