---
name: java-spring
description: Java 25 and Spring Boot 4.1 conventions for the Vinted Republisher project (where Spring goes in the hexagon, configuration properties, controllers implementing the generated OpenAPI interfaces, JPA adapters, transactions, error handling). Use when writing or changing Spring wiring, configuration, controllers, JPA adapters or Java language-level code.
---

# Java 25 and Spring Boot 4.1
Adapted from Jeffallan/claude-skills (MIT), skills `java-architect` and `spring-boot-engineer`, rewritten for this stack and architecture.
Rules from `ddd` and `craftsmanship` win over this skill.

## Java
- Modern Java: `record` for value objects, ids and DTO mapping; `sealed` interfaces + pattern matching `switch` for closed alternatives (statuses, problems); text blocks for JSON in tests.
- No `var`, no Lombok, no `null` returns (see `craftsmanship`).
- Do not use preview features unless the user agrees (they need `--enable-preview` everywhere).

## Where Spring lives
- `domain`: no Spring, no `jakarta.*`, no Playwright.
- `application`: use cases are plain classes; Spring tolerated only for `@Transactional`. Beans are declared in `adapter/config` (`@Configuration` + `@Bean`), not by annotating use cases.
- `adapter/in/web`: `@RestController` classes **implementing the interfaces generated** by `openapi-generator` from the contract. Never write `@GetMapping`/`@PostMapping` by hand for an API endpoint: change the contract first (skill `api-design`).
- `adapter/out/*`: `@Component`/`@Repository` implementing domain outbound ports.
- Constructor injection only, `final` fields, one constructor (no `@Autowired`).

## Configuration
- Typed `@ConfigurationProperties` bound to a `record` (see `ChromeProperties`), validated with `@Validated` + Jakarta constraints when a value is mandatory.
- `application.yml`: shared, no workstation value. `application-local.yml`: profile `local`, reads `.env`. Secrets only from environment variables, never committed.
- No hard-coded URL, port or path in Java code.

## Web adapter
- Map generated DTOs ↔ domain objects in the adapter; never expose a domain object or a JPA entity as a response.
- Domain exceptions are translated to HTTP in one `@RestControllerAdvice`, with the status and body declared in the contract. No stack trace in a response.
- Validate input at the boundary (`@Valid` comes from the generated interfaces when the contract declares constraints); invariants stay in the domain.

## Persistence adapter (JPA + Liquibase)
- Schema owned by Liquibase only (`spring.jpa.hibernate.ddl-auto: validate` or `none`), see skill `postgres-liquibase`.
- JPA entities in `adapter/out/persistence`, suffix `JpaEntity`, separate from domain objects, explicit mapping both ways.
- Spring Data repositories are package-private details behind the domain port.
- Avoid N+1: fetch what an aggregate needs in one query (`JOIN FETCH` or an entity graph); check the SQL in the test logs when in doubt.
- `@Transactional` on the use case that changes an aggregate (one transaction = one aggregate); `readOnly = true` for reads.

## Async and threads
- Long browser actions run off the request thread through the project's executor (`FailureLoggingExecutor`); never swallow an exception in a background task.
- No `Thread.sleep` for waiting: inject `java.time.Clock` for time, wait on conditions for the browser.

## Tests
See skill `tdd` (table of tests per layer). Spring Boot 4.1 specifics: JUnit 6, `spring-boot-starter-webmvc-test` for `@WebMvcTest`, `@MockitoBean` (not the removed `@MockBean`), Testcontainers PostgreSQL via `spring-boot-testcontainers` (`@ServiceConnection`).

## Before using an API
Spring Boot 4 removed or renamed several 3.x APIs. Do not guess: check the Spring Boot 4.1 / Spring Framework 7 documentation or the jars on the classpath before using an annotation or property you are not sure of. Never add a dependency without the user's approval.
