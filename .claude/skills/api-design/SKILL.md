---
name: api-design
description: Contract-first REST API design for the Vinted Republisher project (OpenAPI file written and validated in the spec before code, resource naming, status codes, errors, schemas, examples). Use when adding or changing an endpoint, a schema or an error in src/main/resources/static/openapi/vinted-republisher.yaml, or when writing the API part of a spec.
---

# API design (contract first)
Adapted from Jeffallan/claude-skills (MIT), skill `api-designer`, aligned on this project's existing contract.

## Workflow
1. From the spec's acceptance criteria, list what the pages need to read and to trigger.
2. Write the contract change in `src/main/resources/static/openapi/vinted-republisher.yaml` and put it in the spec (section on the API) for the user to validate, **before any code** (skills `grill-spec`, `implement-user-story`).
3. `mvn compile`: `openapi-generator` regenerates the interfaces and DTOs; the controller in `adapter/in/web` implements them. A gap between contract and code must not compile.
4. Check Swagger UI (`/swagger-ui.html`) renders the change.

## Conventions (follow the existing file)
- OpenAPI **3.0.3**, as the generator is configured. Moving to 3.1 is a decision for the user.
- Paths under `/api/`, nouns in kebab-case (`/api/vinted-session`, `/api/backed-up-listings/{listingId}`).
- Reads: `GET`, never with side effects. Creating: `POST` on the collection, `201` + body or `Location`.
- Commands that are not CRUD (long browser actions) are `POST` on a named sub-resource of the resource they act on, like the existing `/api/vinted-session/check` and `/login`. Long actions return `202 Accepted` and their progress is read by `GET` on the resource.
- `operationId` = English use-case verb from the glossary (`showVintedSession`, `checkVintedSession`): it becomes the generated method name.
- Property names in camelCase, English, from the glossary. Enums in UPPER_SNAKE_CASE, kept in sync with domain enums (see `ContractEnumsTest`).
- Every schema: `required` list, types and formats (`uuid`, `date-time`), constraints the client must respect (`minimum`, `maxLength`, `pattern`), `readOnly` for server-set fields.
- `description` in French (it is user documentation), names in English.
- An `example` on every response schema the pages use.

## Errors
- Every status code an operation can return is declared, with its body schema: `400` invalid input, `404` unknown resource, `409` state conflict (an action already in progress, a backup not validated), `503` Vinted or Chrome unavailable.
- One error shape for the whole API. The project's current errors are business statuses in the body (`SessionProblem`); whether to standardise on RFC 7807 (`application/problem+json`) is an open question to ask the user the first time a generic error shape is needed, not a decision to take alone.
- An error body tells the user what to do (`detail` in French), never a stack trace or an internal class name.

## Avoid
- Verbs in resource paths outside the command sub-resources above (`/getListings`).
- Exposing domain or persistence internals (database ids that are not domain ids, JPA field names).
- Pagination, versioning, rate limiting or authentication "just in case": local single-user tool, YAGNI. Add them only when a spec asks (for example a wardrobe too large to list in one response).
- Breaking an operation the pages already call without updating the page in the same commit.
