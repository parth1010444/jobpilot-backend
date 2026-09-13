# JobPilot

Backend-first intelligent job application tracker. This repository is the primary portfolio deliverable: a **modular monolith** on Spring Boot. A frontend will come much later.

**Current scope: Phase 1 foundation + Phase 2 JWT authentication + Phase 3 application tracking + Phase 4 interview management.** Messaging, caches, reminders, analytics, and a UI remain out of scope.

## Architecture

JobPilot is organized as a single deployable Spring Boot application with package-level module boundaries. Later phases add behavior inside these packages; they do not introduce a microservice split.

```mermaid
flowchart TB
  client[HTTP clients / later frontend]
  subgraph api [HTTP edge]
    controllers[Thin controllers]
    security[Spring Security + JWT]
    advice[GlobalExceptionHandler]
    actuator[Actuator /health]
  end
  subgraph modules [Domain modules]
    auth[auth]
    user[user]
    application[application]
    interview[interview]
    resume[resume]
    reminder[reminder]
    notification[notification]
    jobanalysis[jobanalysis]
    recommendation[recommendation]
    analytics[analytics]
    event[event]
  end
  subgraph platform [Platform]
    common[common]
    config[config]
    infra[infrastructure]
    jpa[Spring Data JPA]
    flyway[Flyway]
  end
  pg[(PostgreSQL)]

  client --> controllers
  client --> actuator
  client --> security
  security --> controllers
  controllers --> modules
  controllers --> advice
  modules --> common
  modules --> infra
  modules --> jpa
  jpa --> flyway
  flyway --> pg
```

Package root: `com.jobpilot`. Controllers stay thin; business logic lives in services. JPA entities are not exposed in API responses — use DTOs.

## Tech stack

| Piece | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Build | Gradle (Kotlin DSL) |
| Persistence | Spring Data JPA + Flyway |
| Database | PostgreSQL 16 (H2 for tests) |
| Security | Spring Security + JWT (jjwt) + BCrypt |
| Ops | Spring Boot Actuator (`/actuator/health`) |
| Packaging | Dockerfile + Docker Compose |

Hibernate `ddl-auto` is **`none`** on the main profile. Schema changes go through Flyway (`V1__init.sql`, `V2__auth_users.sql`, `V3__applications.sql`, `V4__interviews.sql`).

## Prerequisites

- JDK 21
- Docker (for PostgreSQL, and optionally the app image)

## Environment variables

Copy [`.env.example`](.env.example) to `.env` and adjust. Nothing secret is committed.

| Variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_PORT` | HTTP port | `8080` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `local` (compose / example) |
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/jobpilot` |
| `SPRING_DATASOURCE_USERNAME` | DB user | `jobpilot` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `changeme` |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_PORT` | Compose Postgres service | `jobpilot` / `jobpilot` / `changeme` / `5432` |
| `JOBPILOT_JWT_SECRET` | HMAC secret for access tokens | **local/test default only — override in production** |
| `JOBPILOT_JWT_EXPIRATION_MS` | Access token lifetime (ms) | `86400000` (24h) |

> **Production:** you **must** set `JOBPILOT_JWT_SECRET` to a long random value (e.g. `openssl rand -base64 48`). The YAML default is for local development and tests only.

## Local run

### 1. PostgreSQL via Docker Compose

```bash
docker compose up -d postgres
```

This publishes Postgres on `localhost:5432` with the defaults above.

### 2. Application via Gradle

```bash
./gradlew bootRun
```

Or with the local profile (more verbose SQL logging):

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Useful URLs:

- `GET /api/v1/ping` — trivial ping (public)
- `GET /actuator/health` — Actuator health (public)
- `POST /api/auth/register` / `POST /api/auth/login` — auth (public)
- `GET /api/users/me` — current user (requires `Authorization: Bearer <token>`)
- `/api/applications` — application CRUD (requires Bearer JWT)
- `/api/applications/{applicationId}/interviews` and `/api/interviews/{id}` — interview management (requires Bearer JWT)
- Errors use a fixed JSON shape: `timestamp`, `status`, `error`, `message`, `path`

### 3. Optional: app container as well

```bash
docker compose --profile app up --build
```

The app container talks to the `postgres` service on the compose network. Pass `JOBPILOT_JWT_SECRET` via the environment when using this path.

## Phase 2 — JWT authentication

### Endpoints

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Public | Body: `email`, `password` (min 8), optional `name`. Returns JWT + user. Duplicate email → **409**. |
| `POST` | `/api/auth/login` | Public | Body: `email`, `password`. Returns JWT + user. Bad credentials → **401**. |
| `GET` | `/api/users/me` | Bearer JWT | Current user from `SecurityContext` (never trust a client-supplied user id). Missing/invalid token → **401**. |

Passwords are stored as BCrypt hashes. Access tokens are HS256 JWTs (jjwt). Refresh tokens are out of scope for v1.

### curl examples

```bash
# Register
curl -sS -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"password123","name":"You"}'

# Login
TOKEN=$(curl -sS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"password123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

# Current user
curl -sS http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $TOKEN"
```


## Phase 3 — Application tracking

Authenticated CRUD for job applications with DB-level filtering, status state machine, status history, and optimistic locking.

### Domain

| Field | Notes |
| --- | --- |
| `id`, `userId` | UUID; `userId` always from `SecurityContext` (never from the client body) |
| `company`, `jobTitle` | Required |
| `jobUrl`, `location`, `employmentType`, `source` | Optional |
| `status` | `SAVED`, `APPLIED`, `OA`, `INTERVIEW`, `OFFER`, `REJECTED`, `WITHDRAWN` |
| `source` | `LINKEDIN`, `COMPANY_WEBSITE`, `REFERRAL`, `RECRUITER`, `JOB_PORTAL`, `OTHER` |
| `employmentType` | `FULL_TIME`, `PART_TIME`, `CONTRACT`, `INTERNSHIP`, `TEMPORARY`, `OTHER` |
| `salaryMin`, `salaryMax` | Optional integers |
| `jobDescription`, `notes` | Optional text |
| `appliedAt`, `createdAt`, `updatedAt` | Timestamps |
| `version` | `@Version` optimistic lock — required on `PATCH` |

Status transitions are validated by `ApplicationStatusTransitionValidator`. Every status change (including create) writes a row to `application_status_history`.

### Endpoints

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| `POST` | `/api/applications` | Bearer JWT | Create. Default status `SAVED`. |
| `GET` | `/api/applications` | Bearer JWT | Paginated list. Filters: `status`, `q` (company/title/location/notes). Sort e.g. `sort=appliedAt,desc`. |
| `GET` | `/api/applications/{id}` | Bearer JWT | Owner only; missing/other user → **404**. |
| `PATCH` | `/api/applications/{id}` | Bearer JWT | Partial update; body must include `version`. Stale version → **409**. Invalid transition → **400**. |
| `DELETE` | `/api/applications/{id}` | Bearer JWT | Owner only. |

### curl examples

```bash
# Register / login (reuse TOKEN from Phase 2)
TOKEN=$(curl -sS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"password123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

# Create application
curl -sS -X POST http://localhost:8080/api/applications \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "company":"Google",
    "jobTitle":"Software Engineer",
    "jobUrl":"https://careers.google.com/jobs/1",
    "location":"Mountain View",
    "employmentType":"FULL_TIME",
    "source":"LINKEDIN",
    "status":"SAVED",
    "salaryMin":150000,
    "salaryMax":200000,
    "notes":"Dream role"
  }'

# List / filter / search
curl -sS "http://localhost:8080/api/applications?status=INTERVIEW&page=0&size=20&q=google&sort=appliedAt,desc" \
  -H "Authorization: Bearer $TOKEN"

# Get one
curl -sS http://localhost:8080/api/applications/<id> \
  -H "Authorization: Bearer $TOKEN"

# Patch (include version from GET/create response)
curl -sS -X PATCH http://localhost:8080/api/applications/<id> \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"version":0,"status":"APPLIED","notes":"Submitted"}'

# Delete
curl -sS -X DELETE http://localhost:8080/api/applications/<id> \
  -H "Authorization: Bearer $TOKEN"
```

## Phase 4 — Interview management

Authenticated interview-round CRUD is isolated through the owning application. The API never accepts a user id: both parent and interview lookups use the authenticated principal, and resources belonging to another user return **404**.

Each interview has a positive `roundNumber` that is unique within its application, a `type` (`OA`, `TECHNICAL`, `SYSTEM_DESIGN`, `MANAGERIAL`, `HR`, `OTHER`), a `status` (`SCHEDULED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`), scheduling details, optional notes/feedback, timestamps, and an optimistic-lock `version`. Status transitions are `SCHEDULED` → `COMPLETED`, `CANCELLED`, or `NO_SHOW`; terminal statuses cannot transition. Creating an interview intentionally **does not change the parent application status** in Phase 4.

### Endpoints

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/api/applications/{applicationId}/interviews` | Create a round. Status defaults to `SCHEDULED`. Duplicate round → **409**. |
| `GET` | `/api/applications/{applicationId}/interviews` | Paginated; defaults to `roundNumber,scheduledAt` ascending. |
| `GET` | `/api/interviews/{id}` | Get an owned interview. |
| `PATCH` | `/api/interviews/{id}` | Partial update; optional `version` detects stale writes. Invalid transition → **400**. |
| `DELETE` | `/api/interviews/{id}` | Delete an owned interview. |

### curl examples

```bash
# Create (reuse TOKEN and an application id from earlier examples)
curl -sS -X POST http://localhost:8080/api/applications/<applicationId>/interviews \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "roundNumber":1,
    "type":"TECHNICAL",
    "scheduledAt":"2026-10-10T15:00:00Z",
    "interviewer":"Ada Lovelace",
    "meetingLink":"https://meet.example/round-1"
  }'

# List in round/schedule order
curl -sS 'http://localhost:8080/api/applications/<applicationId>/interviews?page=0&size=20' \
  -H "Authorization: Bearer $TOKEN"

# Get one
curl -sS http://localhost:8080/api/interviews/<id> \
  -H "Authorization: Bearer $TOKEN"

# Complete with optional feedback (version is from create/get)
curl -sS -X PATCH http://localhost:8080/api/interviews/<id> \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"version":0,"status":"COMPLETED","feedback":"Strong technical performance"}'

# Delete
curl -sS -X DELETE http://localhost:8080/api/interviews/<id> \
  -H "Authorization: Bearer $TOKEN"
```

## Tests and build

Tests use an in-memory H2 database (PostgreSQL compatibility mode) so they do not require Docker.

```bash
./gradlew test
./gradlew build
```

## Planned later phases (not implemented)

Phases 5–13 are planned and **not** present here. Expected later work includes resumes, reminders, notifications, job analysis, recommendations, analytics, and a frontend. Do not treat remaining placeholder packages as working features.

## License

No license has been chosen yet.
