---
name: postgres-liquibase
description: PostgreSQL 17 and Liquibase rules for the Vinted Republisher project (changesets, column types, constraints, indexes driven by EXPLAIN, Testcontainers). Use when creating or changing a table, a Liquibase changeset, a query, or when a query is slow.
---

# PostgreSQL 17 and Liquibase
Adapted from Jeffallan/claude-skills (MIT), skill `postgres-pro`, reduced to what a single-user local tool needs (no replication, pooling or VACUUM tuning).

## Liquibase
- The schema changes only through changesets under `src/main/resources/db/changelog/`, included from `db.changelog-master.yaml`.
- One changeset per logical change, `id` explicit, `author` set; written in YAML like the master file.
- **Never modify a changeset that has already run** (on the dev database or in a commit): add a new one. Liquibase checksums would fail otherwise.
- Provide a `rollback` when Liquibase cannot infer it (data changes, raw SQL).
- The changeset goes in the same commit as the JPA adapter that needs it.

## Schema
- Table and column names in English, `snake_case`, from the glossary (`backed_up_listing`, not `annonce_sauvegardee`).
- Types: `uuid` for ids (not `text`), `timestamptz` for instants, `numeric(10,2)` for prices (never `float`/`double`), `text` rather than arbitrary `varchar(n)`, `boolean`, `jsonb` only for data with no query need.
- Invariants that the database can hold too: `NOT NULL`, `UNIQUE`, `CHECK`, foreign keys with an explicit `ON DELETE`.
- Images and other large binaries stay on disk under `data/` (never committed), the database stores their path.

## Queries
- Parameterised queries only (JPA/JPQL parameters, `?`/named parameters); never concatenate input into SQL.
- No `SELECT *` in native queries: list the columns.
- Index only after seeing a real query pattern; check with `EXPLAIN (ANALYZE, BUFFERS)` before and after that the planner uses it. Foreign keys used in joins usually deserve an index.

## Tests
- Persistence adapters are tested against a real PostgreSQL 17 through Testcontainers (same image as `docker-compose.yml`), never H2.
- The test runs the real Liquibase changelog, so a broken changeset fails the build.

## Local database
- `docker compose up -d` starts it; data lives in a Docker volume. Never drop the volume or truncate tables without the user's approval: it holds the backed-up wardrobe.
