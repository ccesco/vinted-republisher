---
name: peer-review
description: Request an independent review of a Vinted Republisher change from a fresh subagent, and handle review feedback with technical rigour (verify, push back, no performative agreement). Use after finishing a user story or a complex fix, before handing back, or when receiving review comments.
---

# Peer review
Adapted from obra/superpowers (MIT), skills `requesting-code-review` and `receiving-code-review`, with additions from Jeffallan/claude-skills (MIT), skills `code-reviewer` and `security-reviewer`.

## Requesting a review
When: after a user story (before its closing commit), after a complex bug fix, when stuck. Optional for a trivial change.

1. Range: `BASE=$(git rev-parse HEAD~1)` (or the commit before the US), `HEAD=$(git rev-parse HEAD)`; uncommitted work → review `git diff`.
2. Dispatch a `general-purpose` subagent with a crafted brief, **never** the session history:
   ```
   Review the change {BASE}..{HEAD} (or the working-tree diff) of the Vinted Republisher project.
   What it does: {one-paragraph summary}.
   Requirements: specs/{US-XX-...}.md, section "Tests d'acceptation".
   Read CLAUDE.md and the skills ddd, tdd, craftsmanship in .claude/skills/.
   Check: every Gherkin scenario is covered by a test; behaviour driven by tests;
   English ubiquitous language matching docs/glossary.md; domain free of frameworks,
   dependencies adapter -> application -> domain; controllers implement the generated
   OpenAPI interfaces; YAGNI (nothing beyond the spec); no var, no null returns,
   no dead code; correctness bugs and missing error cases; tests assert
   behaviour with precise values and cover error paths; N+1 queries;
   security (see the list in the peer-review skill).
   First state the change's intent in one sentence; if you cannot, say so.
   Do not modify files. Answer with: intent, strengths, issues ranked
   Critical / Important / Minor with file:line, why and a suggested fix,
   questions for the author, then a verdict.
   ```
   Paste the security list below into the brief when the change touches the browser adapter, the API, the pages, configuration or persistence.
3. Act on it with the rules below: Critical now, Important before handing back, Minor reported to the user.

## Security checks for this project
- No secret, cookie, session token or personal data committed (`.env`, `data/`), logged or returned by the API.
- Chrome debug port and the app bound to `127.0.0.1` only; no endpoint that lets a page drive Chrome to an arbitrary URL.
- SQL parameterised, never concatenated (skill `postgres-liquibase`).
- Static pages: Vinted content (titles, descriptions) inserted with `textContent`, never `innerHTML` (stored XSS from a listing).
- Files written under `data/` with names built from ids, never from raw Vinted input (path traversal).
- Destructive actions on the real Vinted account (delete, republish) guarded by an explicit user validation.
- New dependency → ask the user; known vulnerable version → report it.
For a deeper pass, the built-in `security-review` skill reviews the pending changes.

## Receiving feedback (subagent, tool or user)
1. Read all of it before acting.
2. Restate each item technically; unclear item → ask **before implementing anything** (items may be linked).
3. Verify against the code: is it true here? does it break something? is there a reason for the current code? does it conflict with the spec or a decision of the user?
4. Implement one item at a time, test each (skill `tdd`), then skill `verification-before-completion`.
   Order: blocking (bug, security) → simple → refactoring.

## Push back when
- The suggestion breaks existing behaviour or an ArchUnit rule.
- It adds something the spec does not ask for (YAGNI): grep for real usage first.
- It contradicts a validated spec or a user decision → the user decides, not the reviewer.
- The reviewer lacks context.

Push back with facts (code, test, spec line). Wrong pushback → say "checked, you are right because X" and fix, no long apology.

## Tone
- Feedback from the user is trusted: understand, then act; still ask if the scope is unclear.
- External or subagent feedback: sceptical, but checked carefully.
- No performative agreement ("Great point!", "You're absolutely right!"), no thanks: state the fix ("Fixed: X in `File.java:42`") or just do it.
