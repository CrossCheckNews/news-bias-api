# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.


## Project Overview
`news-bias-api` is a Spring Boot 3.x REST API for cross-checking news bias. It uses Java 21, Spring Data JPA, and H2 (in-memory database for development).
This project is a "news perspective comparison service".

The goal is NOT to summarize news,
but to compare how different media outlets (by country and political leaning)
report the same issue.

## Key Features
- Collect news headlines from multiple publishers
- Cluster articles into topics (same issue)
- Generate 1–2 line AI summaries per topic
- Display headlines grouped by publisher for comparison

## Constraints
- Do NOT store or redistribute article bodies
- Use only headlines and links
- Avoid over-engineering
- Focus on backend architecture and API design (portfolio purpose)

## Tech Stack

- **Java 21** with Spring Boot 3.5.x
- **Spring Data JPA** — ORM layer
- **H2** — in-memory DB (runtime only; swap for a persistent DB in production)
- **Lombok** — boilerplate reduction (`@Getter`, `@Builder`, etc.)
- **Spring Validation** — request validation (`@Valid`, `@NotBlank`, etc.)
- **Gradle** — build tool

## Common Commands

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

## Package Structure

Base package: `com.crosschecknews.api`

Recommended layered structure (not yet created):
```
controller/   — REST controllers (@RestController)
service/      — Business logic (@Service)
repository/   — Spring Data JPA repositories (@Repository)
domain/       — JPA entities and domain models (@Entity)
dto/          — Request/response DTOs
```

## Configuration

`src/main/resources/application.properties` — currently minimal. H2 console and datasource config should be added here as development progresses.
