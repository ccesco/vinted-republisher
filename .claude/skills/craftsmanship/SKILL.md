---
name: craftsmanship
description: Software Craftsmanship principles for the Vinted Republisher project (clean code, SOLID, simple design, refactoring, small commits, review checklist). Use when writing, refactoring or reviewing code.
---

# Software Craftsmanship

Working code, readable, easy to change, delivered in small safe steps.

## Principles
- **Simple design** (in priority order): tests pass, intent is clear, no duplication, fewest elements.
- **YAGNI**: nothing that a validated spec does not ask for. No "just in case" abstraction.
- **SOLID**, especially single responsibility and dependency inversion (domain ports).
- **Boy scout rule**: leave the code a little cleaner than you found it, within the scope of the current user story (no unrequested large refactoring).
- **Tell, don't ask**: ask an object to act; do not read its state to decide in its place.
- Behaviour is pinned by tests; refactoring never changes behaviour.

## Java code
- Short methods (~10 lines), one level of abstraction, few parameters.
- Names **in English**, intention-revealing, from the ubiquitous language (glossary, see skill `ddd`). No abbreviations, no French/English mix.
- Immutability by default: `record`, `final` fields, unmodifiable collections.
- Never return `null`: `Optional` for a legitimate absence, a domain exception for an error.
- Explicit domain exceptions (`BackupNotValidated`); never an empty `catch` or a generic `RuntimeException`.
- No comment that paraphrases the code; a comment explains *why*.
- No dead code, no commented-out code, no `System.out`.
- Constructor injection only; no `@Autowired` on a field.
- No new Maven dependency without the user's approval.

## Commits
- Small, atomic, `mvn test` green on every commit.
- Message **in French**, imperative mood, prefixed by the user story: `US-05 Sauvegarder le prix d'une annonce`.
- Keep refactoring and behaviour changes in separate commits when possible.

## Review checklist (before handing back)
- [ ] Every added behaviour was driven by a test (skill `tdd`).
- [ ] Names are in English, speak the domain language and match the glossary.
- [ ] `domain` depends on no framework (skill `ddd`, enforced by ArchUnit).
- [ ] No duplication, no dead or commented-out code.
- [ ] Every scenario of the spec's "Tests d'acceptation" section is automated and green (or verified, if manual).
- [ ] Nothing beyond the scope of the spec.
- [ ] `mvn test` green, no ignored test.
- [ ] Spec updated in the same commit.

Report any deliberate technical debt to the user instead of hiding it.
