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

## Working Style
Default to action.
Do not stop at suggestions only.
When implementation is requested, implement it.

However, do not blindly code.
Before making changes:
1. inspect the current codebase structure
2. understand related files and dependencies
3. make a short plan
4. implement
5. verify
6. report what changed and what was verified

Do not ask clarifying questions unless absolutely necessary.
Make reasonable assumptions and proceed.

If a requirement is ambiguous, choose the simplest reasonable implementation that fits the project purpose.


## Verification Rules
Verification is required.

For every meaningful change:
- run or write relevant tests where appropriate
- verify compilation/build success when possible
- check for broken imports / missing dependencies
- validate API/request-response consistency
- ensure new code follows existing project conventions
- explicitly report what was verified and what was not verified

If tests do not exist, add the smallest useful test or validation you can.
If full automated verification is not possible, do lightweight but concrete verification instead.
Never claim something is verified unless it was actually checked.
</Verification Rules>

## Investigate Before Answering
Never guess about code you have not opened.
If the user references a specific file, read that file before answering.
Before answering any question about the codebase, investigate and read the relevant files.
Do not make claims about code before investigation unless the answer is certain and does not depend on unseen code.
Prioritize grounded, hallucination-free answers based on the actual codebase.
</Investigate Before Answering>

## Architecture Rules
- Prefer Controller -> Service -> Repository structure
- Keep DTO usage practical, not excessive
- Keep entity relationships simple
- Avoid unnecessary design patterns
- Avoid creating too many layers for a small feature
- Use naming that is easy to explain in a portfolio
- For clustering, prefer a simple and explainable approach first
- Do not introduce advanced NLP or vector infrastructure unless explicitly requested