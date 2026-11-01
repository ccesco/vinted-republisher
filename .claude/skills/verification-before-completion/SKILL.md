---
name: verification-before-completion
description: Evidence before claims for the Vinted Republisher project (run the command, read its output, then report). Use before saying work is done, fixed or passing, before committing, ticking a spec criterion or handing back to the user.
---

# Verification before completion
Adapted from obra/superpowers (MIT).

**Iron law: no completion claim without fresh evidence.** Not run in this turn → not verified.

## Gate
Before any claim ("done", "fixed", "green", "it works"):
1. Which command proves it?
2. Run it, full and fresh.
3. Read the output: exit code, number of tests, failures, errors, skipped.
4. Report what the output says, with the decisive line. If it contradicts the claim, report the real state.

## What proves what
| Claim | Evidence | Not enough |
|-------|----------|------------|
| Tests pass | `mvn test`: `Tests run: N, Failures: 0, Errors: 0, Skipped: 0` | a previous run, one test class |
| Ready to hand back | `mvn test -Pbrowser` green (Chrome tests included) | `mvn test` alone |
| Architecture respected | ArchUnit tests green in that run | "dependencies look right" |
| Contract respected | build compiles against the generated OpenAPI interfaces | Swagger UI displays |
| Bug fixed | the test reproducing the original symptom passes | code changed |
| Regression test is real | seen red without the fix, green with it | passes once |
| Spec fulfilled | each Gherkin scenario of "Tests d'acceptation" mapped to a green test or verified manually with the user | "tests pass" |
| Subagent finished | `git diff` / `git status` show the changes, then tests run | its report |
| Page works | checked in the browser preview (local app, never the real Vinted account without approval) | API test only |

## Red flags
- "Should", "probably", "seems to".
- Satisfaction before evidence ("Done!", "Perfect").
- Committing, ticking a criterion or setting a spec to `implémentée` without a fresh run.
- Trusting a subagent's or a tool's success report.

## When
Always before: a commit, a spec status change, the `craftsmanship` review checklist, moving to the next scenario, delegating, handing back.
If a check could not be run (Docker down, Chrome unavailable), say so explicitly instead of claiming success.
