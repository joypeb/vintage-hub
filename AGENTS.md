# Repository Guidelines

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## Skills
- Java 25
- Spring boot 4.0.6
- Gradle
- PostgreSQL 17
- Docker Compose
- Spring Data JPA
- QueryDSL
- Flyway

## Project Structure & Module Organization

- This is a single-module Spring Boot backend project built with Gradle. Application code lives under `src/main/java/com/joypeb/vintagehub`, with `VintageHubBeApplication` as the boot entry point. Runtime configuration is in `src/main/resources/application.yml` and profile-specific `application-*.yml` files. Tests mirror the main package under `src/test/java/com/joypeb/vintagehub`.
- Project documentation and product notes are kept in `docs/`, including crawl policy material under `docs/crawling-policy/`.
  - 크롤링 가능 여부 : `docs/crawlability-report.md`
  - 아키텍처 : `docs/architecture.md`
  - 기획, 디자인 : `docs/design.md` 
  - 내부 API 명세 : `docs/internal-api.md` (API 요청/응답 변경 시 반드시 함께 수정)


## Build, Test, and Development Commands

- `./gradlew bootRun`: starts the local Spring Boot application.
- `./gradlew test`: runs the JUnit Platform test suite.
- `./gradlew build`: compiles, runs tests, and produces the build artifacts.
- `./gradlew clean`: removes generated Gradle build output.
- `docker compose up -d`: starts the local PostgreSQL 17 database defined in `docker-compose.yml`.
- `docker compose down`: stops the local PostgreSQL 17 database.

Use the checked-in Gradle wrapper instead of a system Gradle install. The project targets Java 25 via Gradle toolchains, so ensure a compatible JDK is available.

## Coding Style & Naming Conventions

Use standard Java conventions: classes in `PascalCase`, methods and fields in `camelCase`, constants in `UPPER_SNAKE_CASE`, and packages in lowercase. Keep Spring components grouped by feature as the codebase grows, for example `product`, `crawl`, or `admin` packages beneath `com.joypeb.vintagehub`. Prefer constructor injection for Spring dependencies. Match the existing indentation style in Java files and keep YAML configuration two-space indented.

## Testing Guidelines

Tests use Spring Boot's test starter and JUnit 5. Place unit and integration tests under `src/test/java` with names ending in `Test` or `Tests`, such as `VintageHubBeApplicationTests`. Keep tests package-aligned with the production class being tested. Run `./gradlew test` before opening a pull request, and add focused coverage for new services, controllers, and configuration behavior.

## Commit & Pull Request Guidelines

Recent commits mostly use short Conventional Commit style messages, for example `docs: refine vintage hub admin decisions`. Prefer lowercase types such as `feat:`, `fix:`, `docs:`, `test:`, and `refactor:` with a concise imperative summary. Pull requests should include a clear description, linked issue or task when available, test results, and notes for configuration or schema changes. Include screenshots only when API docs, generated reports, or user-visible documentation changed.

## Security & Configuration Tips

Do not commit secrets or local credentials. Keep environment-specific values out of `application.yml`; use profiles, environment variables, or external configuration for sensitive settings. Document new crawler behavior or site-specific rules in `docs/crawling-policy/`.
