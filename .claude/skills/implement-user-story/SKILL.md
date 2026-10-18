---
name: implement-user-story
description: Implement a Vinted Republisher user story end to end (validated spec, acceptance tests, TDD, DDD, craftsmanship). Use whenever asked to code, develop or implement a user story (US) or a feature.
---

# Implement a user story

Mandatory workflow for any production code. It chains the project's three skills:
`ddd`, `tdd` and `craftsmanship`. Read them before starting.

Talk to the user in French. Specs, the glossary's definitions and commit messages are in French; code is in English.

## 1. Check the spec
- Open `specs/US-XX-*.md`. If its status is not `validée`, **stop** and ask the user the open questions.
- The spec must contain a "Tests d'acceptation" section: one Gherkin scenario per acceptance criterion and per error case, each with the name of its automated test (or "manuel"). If some are missing, propose them and wait for validation.

## 2. Model (skill `ddd`)
- List the spec's business terms and check them against `docs/glossary.md` (ubiquitous language, code terms in English).
- Identify aggregates, entities, value objects, domain events and the ports needed.
- Present this model to the user in a few lines **before** writing code whenever a new aggregate or a new term appears.

## 3. Build test-first (skill `tdd`)
- Double loop: first a red acceptance test from one spec scenario, then unit Red → Green → Refactor cycles until it passes.
- One scenario at a time.
- Order: domain → use cases (application) → outbound adapters → inbound adapters.

## 4. Clean up (skill `craftsmanship`)
- Go through the `craftsmanship` review checklist.
- `mvn test` must be green, ArchUnit tests included. No disabled or ignored test.

## 5. Close
- All acceptance tests are green; manual scenarios have been verified with the user.
- Tick the fulfilled acceptance criteria in the spec and set its status to `implémentée`.
- Update `docs/glossary.md` if new terms appeared.
- Spec, glossary, tests and code go in the **same commit**, message formatted as described in `craftsmanship`.
- Summarise for the user: criteria covered, decisions made, remaining debt or questions.
