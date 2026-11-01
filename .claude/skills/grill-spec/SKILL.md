---
name: grill-spec
description: Relentlessly interview the user about a Vinted Republisher spec (one question at a time, with a recommended answer) until every decision is made and the spec can be set to "validée". Use when writing, reviewing or validating a spec, when a spec has open questions, or when the user says "grill me".
---

# Grill the spec

Goal: reach a shared, unambiguous understanding of the user story **before** any code exists.
A spec leaves this skill either `validée` or with an explicit list of what is still blocking.

Talk to the user in French. The spec stays in French.

## Rules of the interview
- **One question at a time.** Wait for the answer before asking the next one.
- **Always give your recommended answer** and why, in one or two lines, so the user can just say "ok".
- **Walk the decision tree**: resolve the decisions other decisions depend on first; when an answer opens a new branch, follow it to the end before moving on.
- **Look before you ask**: if the answer is in the code, the other specs, `../../../CLAUDE.md`, `README.md` or `docs/glossary.md`, read it instead of asking.
- **Challenge, don't accept vagueness**: "ça dépend", "comme d'habitude", "plus tard" get a follow-up question. Point out contradictions with other specs or earlier answers.
- **Never invent a business rule.** If the user does not know, record it under "Questions ouvertes" and say what it blocks.
- After each answer, **update the spec file right away** (Décisions, Critères, Tests d'acceptation…) so nothing is lost if the session stops.

## What to grill (adapt to the story, skip what does not apply)
1. **Purpose and scope**: who benefits, what value, what is explicitly out of scope, dependencies on other user stories.
2. **Vocabulary**: every business term maps to a `docs/glossary.md` entry (English term | French | definition); new terms are named now.
3. **Nominal behaviour**: step by step, inputs, outputs, what the user sees in the dashboard.
4. **States and transitions**: which listing states are involved, which transitions are legal, what happens on an illegal one.
5. **Failure cases**: Vinted unreachable, session expired, captcha, page structure changed, crash in the middle, disk full, database down, partial data.
6. **Data safety**: can anything be lost or deleted? What must be backed up and validated first? Is the original image untouched? Is the action reversible?
7. **Idempotence and recovery**: what if the action runs twice, or resumes after a restart? What about the persistent 5-minute wait?
8. **Vinted account safety**: rate limits, behaviour that could get the account blocked, dry-run mode, what needs the user's explicit confirmation.
9. **Data and persistence**: what is stored, for how long, what schema change (Liquibase) it implies.
10. **Acceptance tests**: every criterion and every failure case has a Gherkin scenario, with an automated test name in English or "manuel" with a reason.
11. **Done**: what the user will check manually before accepting the story.

## Ending
- Summarise the decisions taken, in a short list.
- Show what changed in the spec.
- If nothing is open: propose setting the status to `validée`, and only change it once the user agrees.
- Otherwise: list the remaining open questions and what each one blocks.
