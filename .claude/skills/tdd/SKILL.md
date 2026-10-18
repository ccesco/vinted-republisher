---
name: tdd
description: Test-Driven Development rules for the Vinted Republisher project (acceptance tests from the spec, Red-Green-Refactor, JUnit, AssertJ, tests per hexagonal layer). Use when writing or changing production code or tests.
---

# TDD – Test-Driven Development

No line of production code without a failing test that demands it.

## The cycle
1. **Red**: write *one* test, run it, watch it fail for the right reason (an assertion, not a forgotten compilation).
2. **Green**: write the simplest code that makes it pass. Shortcuts (hard-coded values) are allowed: the next test will force generalisation (triangulation).
3. **Refactor**: with tests green, clean the code *and* the tests (duplication, names, intent). Run the tests again.

Never refactor on red. Never have two failing tests at once.
Show the user the progression (red test → green) for significant steps.

## Double loop: acceptance tests, then TDD
1. **Outer loop**: take one scenario from the spec's "Tests d'acceptation" section and automate it as written. It is red.
2. **Inner loop**: unit Red → Green → Refactor cycles until the scenario turns green.
3. Next scenario. The user story is done when every scenario is green.

- The acceptance test copies the scenario word for word as `// Given // When // Then` comments, and uses the test name given in the spec.
- Location: `src/test/java/fr/vinted/republisher/acceptance/`, one `<Feature>AcceptanceTest` class per feature.
- It goes through inbound ports (use cases) with fakes for outbound ports; `@SpringBootTest` + Testcontainers only when the scenario is about infrastructure.
- A scenario marked "manuel" in the spec is not automated, but must be verified and ticked before closing the user story.
- A missing or vague scenario in the spec: propose it to the user, never invent it.
- Keep the unit test list up to date (tick items, add what you discover).

## Conventions
- JUnit Jupiter + AssertJ (provided by `spring-boot-starter-test`, JUnit 6 with Spring Boot 4.1).
- Class name: `<TestedClass>Test`. Method names **in English**, using glossary terms, describing a business behaviour:
  `backed_up_listing_can_be_deleted()`, not `testDelete()`.
- One behaviour per test. Assert on the observable outcome, not on the implementation.
- `@DisplayName` optional; `@Nested` to group by context.
- Test data through readable builders or factory methods (`aBackedUpListing()`), no copy-paste.

## Tests per layer (hexagonal architecture)
| Layer | Test type | Tools | Spring? |
|---|---|---|---|
| `domain` | unit, the majority | JUnit + AssertJ | no |
| `application` | unit on use cases, outbound ports replaced by **in-memory fakes** | JUnit + AssertJ | no |
| `adapter/out` (JPA, Playwright…) | integration | Testcontainers PostgreSQL, `@DataJpaTest` | yes |
| `adapter/in` (web) | slice | `@WebMvcTest` | yes |
| end to end | a few key scenarios | `@SpringBootTest` + Testcontainers | yes |
| architecture | rules | ArchUnit (`architecture` package) | no |

- Prefer hand-written **fakes** (e.g. `InMemoryListings implements Listings`) over mocks; Mockito only for interactions that are hard to fake.
- Time (the 5-minute wait) goes through an injected `java.time.Clock`: never `Thread.sleep` in a test.
- No test ever touches the real Vinted site. Vinted adapters are tested against local pages or test doubles.

## Example
```java
class ListingTest {

    @Test
    void cannot_be_deleted_without_a_validated_backup() {
        // Given
        var listing = ListingFixtures.aScannedListing();

        // When
        var attempt = catchThrowable(listing::markDeleted);

        // Then
        assertThat(attempt)
            .isInstanceOf(BackupNotValidated.class);
    }
}
```

## Forbidden
- Writing the code first, then tests "for coverage".
- `@Disabled`, commented-out tests, empty assertions.
- Tests that depend on execution order or on the real clock.
