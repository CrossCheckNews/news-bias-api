# Project: Cross Check news
This project is a portfolio-focused backend API for comparing how different media outlets report the same issue.

The goal is NOT to build a full news platform,
but to demonstrate clean backend architecture and data flow.

## Critical Rules (절대 규칙)
- Do NOT store or process full article content (headline + link only)
- Do NOT over-engineer (no microservices, no complex infra)
- Always prefer simple and explainable solutions
- This is a portfolio project → clarity > scalability
- Never guess about code that has not been read
- Always verify changes before completing a task

## Architecture (아키텍쳐)
This project follows a simple layered architecture:
- Controller → Service → Repository

- **Controller**
    - Handles HTTP request/response
    - Validates input (`@Valid`)
    - Returns DTOs only

- **Service**
    - Contains business logic
    - Handles clustering and topic logic
    - Coordinates between repositories

- **Repository**
    - Spring Data JPA interface
    - Handles DB access only

- **Domain**
    - JPA Entities
    - Minimal relationships (avoid complex mapping)

- **DTO**
    - Request/Response models
    - Do not expose entities directly

## Tech Stack

- **Java 21** with Spring Boot 3.5.x
- **Spring Data JPA** — ORM layer
- **H2** — in-memory DB (runtime only; swap for a persistent DB in production)
- **Lombok** — boilerplate reduction (`@Getter`, `@Builder`, etc.)
- **Spring Validation** — request validation (`@Valid`, `@NotBlank`, etc.)
- **Gradle** — build tool

## Build & Test Commands (빌드/테스트)
```bash
# Build
./gradlew build

# Run application
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.crosschecknews.api.SomeTest"

# Clean build
./gradlew clean build
```

## Domain Context (도메인 컨텍스트)

See detailed domain model in:
src/main/java/com/crosschecknews/api/domain/domain.md

### Summary (IMPORTANT)

- Publisher: 언론사 (country + politicalLeaning)
- Article: headline + url only
- Topic: 같은 이슈 묶음
- TopicArticle: Topic-Article 연결

Topic clustering is approximate.
Do NOT store full article content.

## Key Patterns (핵심 패턴)
- Collect news headlines from multiple publishers
- Cluster articles into topics (same issue)
- Generate 1–2 line AI summaries per topic
- Display headlines grouped by publisher for comparison
