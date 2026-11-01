---
name: systematic-debugging
description: Root-cause-first debugging for the Vinted Republisher project (reproduce, trace, one hypothesis at a time, failing test before the fix). Use on any bug, test failure, build failure or unexpected behaviour, before proposing a fix.
---

# Systematic debugging
Adapted from obra/superpowers (MIT), with additions from Jeffallan/claude-skills (MIT), skill `debugging-wizard`.

**Iron law: no fix without a root cause.** A symptom fix is a failure, even when the test turns green.
Applies especially when the bug looks simple, when time is short, or when a first fix did not work.

## 1. Investigate the root cause
- Read the whole error: stack trace, line numbers, Maven/Surefire report, Liquibase or Spring startup log.
- Reproduce reliably: the smallest command (`mvn test -Dtest=...`, an HTTP call to the API). Not reproducible → gather more data, do not guess.
- Check recent changes: `git diff`, last commits, `pom.xml`, `application*.yml`, `.env.example`, Liquibase changelogs.
- Regression with a known good commit: `git bisect` with the reproducing command (`git bisect run mvn -q test -Dtest=...`).
- Across layers (`adapter/in/web` → `application` → `domain` → `adapter/out` → PostgreSQL / Chrome), find **which boundary** breaks: log what enters and leaves each one, run once, then dig into the failing layer only.
- Trace a bad value backwards to where it originates; fix it there, not where it surfaces.
- Browser bugs (`@Tag("browser")`, Chrome over CDP): never inspect the Chrome connected to the real Vinted account without asking the user first.

## 2. Compare with what works
- Find similar working code in the project (another adapter, another use case, another test).
- List every difference, however small. Read a reference (Spring, Playwright, Liquibase docs) completely before applying its pattern.

## 3. One hypothesis at a time
- State it: "I think X is the root cause because Y".
- Test it with the smallest change, one variable at a time. Wrong → new hypothesis; never stack fixes.
- Do not understand something → say so and ask, do not pretend.

## 4. Fix (skill `tdd`)
- First a **red test** reproducing the bug, at the lowest layer that shows it (domain test rather than browser test when possible).
- One fix, targeting the root cause. No "while I'm here" improvement, no bundled refactoring.
- Remove every diagnostic log and temporary code before committing.
- Then skill `verification-before-completion`.
- Report to the user: root cause, evidence (stack trace, log, test), fix, regression test added.

## Three failed fixes: stop
Each fix reveals a new problem elsewhere, or requires a large refactoring → the design is wrong, not the hypothesis.
Stop and discuss the design with the user before a fourth attempt. Never loosen an ArchUnit rule to make a fix pass.

## Red flags: back to step 1
- "Quick fix now, investigate later", "let's try changing X".
- Several changes before running the tests.
- "Skip the test, I'll check by hand."
- Proposing fixes before tracing the data flow.
- The user says "stop guessing", "is that really happening?", "are we stuck?".

## Really no root cause (timing, environment, Vinted itself)
Document what was investigated, add the right handling (explicit wait on a condition, not a fixed `sleep`; clear error message) and report it to the user.
Most "no root cause" cases are an incomplete investigation.
