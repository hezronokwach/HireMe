# HireMe

HireMe is a REST API for hiring out equipment. Equipment owners list machinery (heavy machinery, agricultural, event setup) with a daily rate in Kenyan Shillings. Clients browse the listings and book equipment for a date range, and the owner confirms, completes or cancels the booking.

It is a Spring Boot **modular monolith**: one deployable application split into modules (`user`, `equipment`, `booking`, `shared`). Spring Modulith enforces the module boundaries and carries events between modules.

An Angular web app for it lives in [`frontend/`](frontend/) (see [Frontend](#frontend)).

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Domain Model](#domain-model)
- [Booking Lifecycle](#booking-lifecycle)
- [Security](#security)
- [AI Features](#ai-features)
- [API Reference](#api-reference)
- [Error Handling](#error-handling)
- [Frontend](#frontend)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Testing](#testing)
- [CI/CD](#cicd)
- [Deployment](#deployment)
- [Project Structure](#project-structure)
- [License](#license)

---

## Tech Stack

| Concern            | Technology                                              |
|--------------------|---------------------------------------------------------|
| Language           | Java 25                                                 |
| Framework          | Spring Boot 4.1 (Web, Data JPA, Validation, Security, Actuator) |
| Modularity         | Spring Modulith 2.1 (core, JPA event publication registry) |
| Database           | PostgreSQL 16 (H2 in unit tests, Testcontainers in integration tests), Flyway migrations |
| Authentication     | JWT (jjwt 0.12) and BCrypt password hashing             |
| Boilerplate        | Lombok                                                  |
| Build              | Maven (wrapper included)                                |
| Quality            | JaCoCo, SonarCloud, Qodana, Gitleaks, Trivy             |
| Frontend           | Angular 22 (standalone components, signals, zoneless), Angular Material, Vitest, ESLint; API client generated with OpenAPI Generator |
| Containerisation   | Docker multi-stage build and Docker Compose             |

---

## Architecture

```
com.example.hireme
├── user/        Registration, login, user lookup         (public API: UserService, AuthService, DTOs, Role)
├── equipment/   Equipment listings                       (public API: EquipmentService, EquipmentBookingChecker, DTOs, enums)
├── booking/     Bookings, their lifecycle, booked dates  (public API: BookingService, DTOs, BookingStatus)
├── ai/          Calls to the language model (Groq)       (public API: AiAssistant, AI exceptions, AiStatusResponse)
└── shared/      OPEN module: security, exceptions, integration events, clock, paging, OpenAPI
```

Each module exposes a small public API in its root package (service interfaces, request/response records and enums). Entities, repositories, mappers and service implementations live in an `internal` sub-package that other modules cannot reach. `ModularityTests` runs `ApplicationModules.verify()` to fail the build if a module reaches into another module's internals or if modules depend on each other in a cycle.

### Module dependencies

```
booking ──calls──▶ equipment ──calls──▶ user
   │                   │
   └── implements ◀────┘ EquipmentBookingChecker (interface owned by equipment)
```

- **booking → equipment (synchronous):** to check that a listing takes bookings, read the daily rate, find the equipment's owner and lock the equipment while a booking is confirmed (`EquipmentService.lockForBooking`).
- **equipment → user (synchronous):** to check whether the caller has the `OWNER`/`ADMIN` role.
- **equipment asks booking through an interface:** before deleting a listing, `equipment` calls `EquipmentBookingChecker.hasOpenBookings`. The interface lives in `equipment` and `booking` implements it, so the type dependency still only points from `booking` to `equipment` (calling `booking` directly would be a cycle, which Modulith rejects).

`booking` also publishes `BookingCreatedEvent`, `BookingCancelledEvent` and `BookingCompletedEvent` (in `shared.event`, an **open** module every module can use), and `equipment` publishes `EquipmentStatusChangedEvent` when an owner changes a listing's status. Nothing listens to them yet; they are there for features such as notifications. Because `spring-modulith-starter-jpa` is on the classpath, published events are recorded in an event publication table, and listeners run asynchronously after the publishing transaction commits.

### Database schemas

Each module owns its own PostgreSQL schema. [Flyway](https://flywaydb.org) creates the schemas and tables from the migrations in `src/main/resources/db/migration`, and Hibernate only checks that the entities match them (`ddl-auto: validate`):

| Module    | Schema          | Table       |
|-----------|-----------------|-------------|
| user      | `app_user`      | `users`     |
| equipment | `app_equipment` | `equipment` |
| booking   | `app_booking`   | `bookings`  |

Modules refer to each other's records by ID only (`ownerId`, `equipmentId`, `clientId`). There are no foreign keys or JPA relationships between modules.

### Migrations

| Folder                         | Runs on            | Contents                                                     |
|--------------------------------|--------------------|--------------------------------------------------------------|
| `db/migration/common`          | Every database     | Portable SQL, e.g. `V1__baseline.sql` (all tables)           |
| `db/migration/postgresql`      | PostgreSQL only    | Scripts that need PostgreSQL features, e.g. `V2__drop_full_name_unique.sql` |

- **Schema changes** go in a new script with the next version number, e.g. `V3__add_equipment_photos.sql`. Never edit a script that has already been applied: Flyway checks their checksums and refuses to start.
- **Databases created before Flyway** (by Hibernate `ddl-auto: update`) are marked as version 1 on first start (`spring.flyway.baseline-on-migrate`), so `V1` is skipped and later scripts are applied. Their unique constraints have generated names such as `uk6dotkott2kjsp8vw4d0m25fb7`, so a migration that changes a constraint must look it up by column, as `V2` does.
- **Tests** run on H2 and only use `db/migration/common` (H2 in PostgreSQL mode reports itself as PostgreSQL, so the vendor folder can't be used there).

---

## Domain Model

### User

| Field         | Notes                                                    |
|---------------|----------------------------------------------------------|
| `id`          | Generated                                                |
| `fullName`    | Required (does not have to be unique)                    |
| `email`       | Required, unique, used as the login username             |
| `phoneNumber` | Required, unique, must be a Kenyan mobile number (`07XXXXXXXX` or `+2547XXXXXXXX`) |
| `passwordHash`| BCrypt hash; the raw password must be at least 8 characters |
| `role`        | `CLIENT`, `OWNER` or `ADMIN` (chosen at registration)    |
| `createdAt`   | Set on registration                                      |

### Equipment

| Field          | Notes                                              |
|----------------|----------------------------------------------------|
| `id`           | Generated                                          |
| `ownerId`      | The user who created the listing                   |
| `name`         | Required                                           |
| `category`     | `HEAVY_MACHINERY`, `AGRICULTURAL`, `EVENT_SETUP`   |
| `description`  | Optional, at most 1000 characters (`name` and `location` at most 255) |
| `dailyRateKes` | Required, must be greater than zero                |
| `location`     | Required                                           |
| `status`       | `AVAILABLE` (default) or `UNDER_MAINTENANCE`, set by the owner. Says whether the listing takes bookings; booked dates come from bookings (see [Availability](#availability)) |
| `createdAt` / `updatedAt` | Timestamps                              |

### Booking

| Field          | Notes                                                        |
|----------------|--------------------------------------------------------------|
| `bookingId`    | Generated                                                    |
| `equipmentId`  | The equipment being hired                                    |
| `clientId`     | The user who made the booking                                |
| `startDate`    | Today or later                                               |
| `endDate`      | In the future, not before `startDate`                        |
| `totalCostKes` | `dailyRateKes × (days between start and end + 1)`, so both dates count |
| `status`       | `PENDING`, `CONFIRMED`, `ACTIVE`, `COMPLETED`, `CANCELLED`   |
| `creationDate` | Set when the booking is created                              |
| `clientName`, `clientPhone` | Only in responses to the equipment's owner (or an admin), from the `user` module in one lookup per page. The phone is only given while the booking is `PENDING`, `CONFIRMED` or `ACTIVE`; otherwise it is `null`. Clients never see these fields. |

---

## Booking Lifecycle

```
        create (client)
             │
             ▼
        ┌─────────┐ confirm (owner) ┌───────────┐ start date (job) ┌────────┐ complete (owner) ┌───────────┐
        │ PENDING │ ──────────────▶ │ CONFIRMED │ ───────────────▶ │ ACTIVE │ ───────────────▶ │ COMPLETED │
        └─────────┘                 └───────────┘                  └────────┘                  └───────────┘
             │                            │                            │
             │ cancel (client or owner)   │ cancel (client or owner)   │ cancel (owner only)
             ▼                            ▼                            ▼
        ┌───────────┐ ◀────────────────────┴────────────────────────────┘
        │ CANCELLED │
        └───────────┘
```

| Action   | Who                                   | Allowed from                | Checks and side effects             |
|----------|---------------------------------------|-----------------------------|-------------------------------------|
| Create   | Any `CLIENT`/`ADMIN`                  | n/a                         | Equipment must be `AVAILABLE` and no `CONFIRMED`/`ACTIVE` booking may overlap the dates (`409`). Other `PENDING` requests for the same dates are fine. |
| Confirm  | Owner of the equipment                | `PENDING`                   | Re-checks the dates (`409` if taken), then cancels every other `PENDING` booking that overlaps them. |
| Activate | `BookingActivationJob` (no user)      | `CONFIRMED`, once `startDate` is today or earlier | Publishes `BookingActivatedEvent` |
| Complete | Owner of the equipment                | `CONFIRMED` or `ACTIVE`     | none                                |
| Cancel   | The booking's client or the equipment owner; only the owner once it is `ACTIVE` | anything except `COMPLETED`/`CANCELLED` | Frees the dates (`403` for a client on an `ACTIVE` booking) |

**Activation:** `BookingActivationJob` runs every hour at minute 5 (`app.bookings.activation-cron`) and once on startup, so bookings due while the app was down catch up straight away. "Today" is the date in `app.time-zone` (Africa/Nairobi), whatever the server's own time zone is. It is safe to run again: each booking is switched with `UPDATE ... WHERE status = 'CONFIRMED'`, so a booking cancelled in the meantime stays cancelled, and the update also bumps the booking's `version`, so a request still holding the old copy gets `409`.

### Availability

A piece of equipment is booked on a day when a `CONFIRMED` or `ACTIVE` booking covers that day. **Both the start and end dates are booked days**, the same as for pricing: a booking for the 10th to the 12th blocks a new booking starting on the 12th, but not one starting on the 13th.

Two confirmations can't double-book the same dates: confirming a booking locks the equipment's row (`SELECT ... FOR UPDATE`) until the transaction ends, so confirmations for the same equipment run one after the other, and the second one sees the first.

---

## AI Features

The `ai` module is the one place that talks to a language model. It uses [Groq](https://groq.com) through its OpenAI-compatible API, with [Spring AI](https://spring.io/projects/spring-ai) (`spring-ai-openai`, no starter) and the `openai/gpt-oss-120b` model (set `HIREME_AI_MODEL` to use another one; Groq retires models from time to time, see [its model list](https://console.groq.com/docs/models)). Feature modules call the `AiAssistant` interface with a system and a user prompt, and get back either text or a record parsed from JSON. They never see HTTP, keys or retries.

- **Off by default.** It's on only with `HIREME_AI_ENABLED=true`, which also needs `GROQ_API_KEY`. The app refuses to start if AI is enabled without a key. While it's off, the app works normally and AI calls return `503`.
- **Failures don't spread.** If Groq is down, slow (`hireme.ai.timeout`, default 20 s), rate-limiting us, or returns an unusable answer, the call fails with `503` ("AI features are unavailable right now") after `hireme.ai.max-retries` retries. Features with a non-AI fallback can catch `AiUnavailableException`.
- **Per-user rate limit:** `hireme.ai.requests-per-minute` (default 10) AI calls per user in any rolling minute, then `429`. It protects the shared Groq quota. It's kept in memory, so it applies per app instance and resets on restart.
- **`POST /api/v1/equipment/ai/describe`** (OWNER, ADMIN) drafts a listing from a few details, for example `{"name": "Massey Ferguson 375", "category": "AGRICULTURAL", "location": "Nakuru", "notes": "2019, 75 hp, with operator"}`. It returns a `title` (at most 80 characters) and a `description` (at most 600). It also returns a `suggestedDailyRateKes` with a one-line `rateReason`, based on up to 20 listings in the same category (nearby first, `comparableListings` says how many). **Nothing is saved:** the owner accepts or edits the draft and then creates the listing as usual. The answer is checked before it is returned:
  - **Missing text:** a draft without a title or description is treated as a failed call (`503`).
  - **Long text:** it is cut on a word boundary to fit.
  - **No comparables:** there is no rate suggestion.
  - **Out-of-range rate:** a rate below half the cheapest or above twice the dearest comparable listing is dropped.

  The prompts live in `src/main/resources/prompts/` (`describe-equipment.st`, `describe-equipment-request.st`). The owner's notes are passed as information, and the model is told to ignore any instructions inside them.
- **`POST /api/v1/equipment/ai/assistant`** (any logged-in user): **the hire assistant**, a conversation that finds, compares and prices listings. Send the whole conversation each time (`messages`: `role` `USER` or `ASSISTANT`, `text`, and for assistant messages the `listingIds` and `from`/`to` days they were about); the server keeps nothing.
  - **Tools:** the model gets read-only tools over the real data and answers only from them:
    - `searchListings`: the normal search, with the model's filters cleaned up as below. Keywords are kept with a category, and it falls back to the category alone when nothing mentions the keywords. With days, it returns only free listings, each with the total for those days.
    - `getListings`: details and descriptions of listings.
    - `checkDates`: free or not, plus the total, for several listings at once.
    - `reply`: how the model hands back its answer.
  - **Trust:** the response's `listings` are cards the server builds from the database, only for listings the tools returned in this conversation, with the price and availability (`quote`: days × rate, both days counted) worked out by the server. So the model can't invent a listing, price or free day. It never books anything, and user text is treated as data, not instructions.
  - **Context:**
    - The server puts today's dates for "tomorrow", "this weekend", "next weekend" and "next week" in the prompt, because models get day arithmetic wrong.
    - Earlier answers are replayed with the listings they showed (with the start of each description) and their days, so "the second one" or "does it come with a driver?" work.
    - When an answer has no days, it keeps the ones the conversation was about.
  - **Robustness against Groq and gpt-oss quirks:**
    - The model sometimes answers in plain text instead of calling `reply`. Then the text is used, and the listings it names get cards.
    - Its hidden reasoning isn't sent back (Groq rejects it).
    - A call to a tool that doesn't exist is retried once.
    - When Groq's per-minute token limit says "try again in Xs" (up to 5 s), the server waits and retries. Beyond that the answer is `503` "The AI is busy right now". On the free tier (8,000 tokens a minute) that allows roughly one or two assistant turns a minute.
  - **Limits:** each message counts once against the per-user AI limit. At most 20 messages per request, of which the model sees the latest 12.
- **`POST /api/v1/equipment/ai/search`** (any logged-in user) takes a sentence, e.g. `{"query": "I need a tractor in Nakuru next weekend, under 8,000 a day"}`. The model only turns it into the filters of `GET /api/v1/equipment` (category, location, rates, keywords and the `availableFrom`/`availableTo` dates, worked out from today in Africa/Nairobi), and the normal search runs with them. It never queries the database itself. The response carries those `filters` for the UI to show as editable chips, `interpretedBy` (`AI` or `KEYWORDS`), and the page of `results`. Everything the model returns is checked:
  - **Unknown category:** dropped.
  - **Dates:** unparsable dates are dropped, past days are moved up to today, backwards ranges are swapped, and ranges over a year are dropped.
  - **Rates:** negative or backwards rates are dropped.
  - **Keywords:** used only when there is no category.

  If AI is off, failing or over the user's limit, the search still answers: the query's words are matched against listings instead (`interpretedBy: KEYWORDS`, with a `notice`).
- **`GET /api/v1/ai/status`** returns `{ "enabled": true, "model": "...", "requestsPerMinute": 10 }`, so the frontend can hide AI buttons when they would only fail.

To turn it on, get a key at [console.groq.com/keys](https://console.groq.com/keys) and add it to `.env` (which is gitignored; never commit a key, and Gitleaks scans every PR):

```dotenv
HIREME_AI_ENABLED=true
GROQ_API_KEY=<your Groq key>
```

---

## Security

- **Stateless JWT authentication.** `POST /api/v1/auth/login` returns a signed HS256 token. The subject is the user's email and a `role` claim holds the role. Tokens expire after 24 hours (`app.jwt.expiration-ms`).
- Send the token on every protected request:
  ```
  Authorization: Bearer <token>
  ```
- `JwtAuthenticationFilter` validates the token, loads the user through `CustomUserDetailsService` and fills in the security context. Roles become authorities with a `ROLE_` prefix (`ROLE_CLIENT`, `ROLE_OWNER`, `ROLE_ADMIN`).
- **Authorisation** uses `@PreAuthorize` on each endpoint (method security is enabled). Ownership checks, such as "is this your equipment?", happen in the service layer.
- **Public routes:** `/api/v1/auth/**`, `/actuator/**` and the API docs (`/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`).
- **Browsing is public too**, because a visitor only needs an account to book:
  - `GET /api/v1/equipment`, `GET /api/v1/equipment/{id}` and `GET /api/v1/equipment/{id}/availability` (numeric ids only, so `/equipment/mine` stays private);
  - `GET /api/v1/ai/status`;
  - `POST /api/v1/equipment/ai/assistant`. Signed-out visitors share one AI rate-limit budget (`AiAssistant.GUEST_USER_ID`).
- **Every other route needs a valid token.**
- **CORS** allows only `app.frontend-url` (default `http://localhost:4200`, set with `FRONTEND_URL`), with credentials, for `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- **CSRF** uses a cookie-based token repository (`XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header) for non-API routes. It is turned off for `/api/v1/**` and `/actuator/**` because those requests use bearer tokens, not cookies.
- Passwords are hashed with BCrypt.

---

## API Reference

Base path: `/api/v1`. Request and response bodies are JSON.

### Interactive docs (OpenAPI / Swagger UI)

With the app running, open **http://localhost:8080/swagger-ui.html** to browse and call every endpoint. The machine-readable OpenAPI 3.1 spec is at **http://localhost:8080/v3/api-docs** (served by [springdoc-openapi](https://springdoc.org)).

1. Call `POST /api/v1/auth/login` (or register first) and copy the `token` from the response.
2. Click **Authorize**, paste the token (without the `Bearer ` prefix) and click **Authorize**.
3. Every other request you send from the page now carries `Authorization: Bearer <token>`.

The spec documents each operation's success code, its error responses (all with the `ErrorResponse` body described in [Error Handling](#error-handling)) and readable operation IDs such as `searchEquipment` and `confirmBooking`. A client generator can turn it into a typed client, for example for the Angular frontend:

```bash
npx @openapitools/openapi-generator-cli generate -i http://localhost:8080/v3/api-docs -g typescript-angular -o src/app/api
```

To turn the docs off, for example in production, set `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false`.

The sections below describe the same endpoints in prose.

### Auth: `/api/v1/auth` (public)

#### `POST /auth/register` → `201 Created`

```json
{
  "fullName": "Jane Wanjiku",
  "email": "jane@example.com",
  "phoneNumber": "0712345678",
  "password": "secret123",
  "role": "OWNER"
}
```

Response (`UserResponse`):

```json
{
  "id": 1,
  "fullName": "Jane Wanjiku",
  "email": "jane@example.com",
  "phoneNumber": "0712345678",
  "role": "OWNER",
  "createdAt": "2026-10-26T08:00:00Z"
}
```

Returns `409` if the email is already registered and `400` on validation errors.

#### Demo owner: `GET /auth/demo` and `POST /auth/demo-owner`

Only on deployments seeded with demo data (`HIREME_DEMO_LISTINGS=true`; see [Configuration](#configuration)).

- **`GET /auth/demo`** returns `{ "ownerAvailable": true, "ownerEmail": "demo-owner@hireme.example" }`, so the login page knows whether to offer the demo.
- **`POST /auth/demo-owner`** signs in as the demo owner without a password. It returns the same body as login, or `404` when the server has no demo data.

#### `POST /auth/login` → `200 OK`

```json
{ "email": "jane@example.com", "password": "secret123" }
```

Response (`AuthResponse`):

```json
{
  "token": "<jwt-token>",
  "userId": 1,
  "role": "OWNER",
  "expiresAt": "2026-10-27T08:00:00Z"
}
```

Returns `401` for a wrong email or password.

### Equipment: `/api/v1/equipment`

| Method   | Path              | Roles          | Description                                       |
|----------|-------------------|----------------|---------------------------------------------------|
| `POST`   | `/equipment`      | OWNER, ADMIN   | Create a listing (`201`). The caller becomes the owner. |
| `GET`    | `/equipment`      | Public         | Search equipment, one page at a time (see [Paging and filters](#paging-and-filters)). |
| `GET`    | `/equipment/mine` | OWNER, ADMIN   | The caller's own listings, any status, one page at a time (`page`, `size`, `sort` as for the search). |
| `GET`    | `/equipment/{id}` | Public         | Get one item (`404` if it does not exist).        |
| `GET`    | `/equipment/{id}/availability` | Public | Booked dates between `?from=` and `?to=` (ISO dates, both optional: defaults to the next 90 days, at most 366 days). |
| `PUT`    | `/equipment/{id}` | OWNER, ADMIN   | Update a listing you own (ADMIN: any listing). Only the fields you send are changed; send `"status": "UNDER_MAINTENANCE"` to stop new bookings. |
| `POST`   | `/equipment/ai/assistant` | Public (guests share one rate-limit budget) | Talk with the hire assistant; returns its reply, listing cards with prices and suggested follow-ups (see [AI Features](#ai-features)). |
| `POST`   | `/equipment/ai/search` | Authenticated | Search with a sentence; returns the interpreted filters and the results (see [AI Features](#ai-features)). |
| `POST`   | `/equipment/ai/describe` | OWNER, ADMIN | Draft a title, description and daily rate with AI (see [AI Features](#ai-features)). Nothing is saved. |
| `DELETE` | `/equipment/{id}` | OWNER, ADMIN   | Delete a listing you own (ADMIN: any listing) (`204`). Refused (`409`) while it has `PENDING`, `CONFIRMED` or `ACTIVE` bookings that end today or later. |

Create request (`CreateEquipmentRequest`):

```json
{
  "name": "CAT 320 Excavator",
  "category": "HEAVY_MACHINERY",
  "description": "20-tonne crawler excavator",
  "dailyRateKes": 25000,
  "location": "Nairobi"
}
```

The update request (`UpdateEquipmentRequest`) has the same fields plus `status`, and none of them are required. Response (`EquipmentResponse`):

```json
{
  "id": 1,
  "ownerId": 1,
  "name": "CAT 320 Excavator",
  "category": "HEAVY_MACHINERY",
  "description": "20-tonne crawler excavator",
  "dailyRateKes": 25000,
  "location": "Nairobi",
  "equipmentStatus": "AVAILABLE",
  "createdAt": "2026-10-26T08:00:00Z",
  "updatedAt": null
}
```

#### Paging and filters

`GET /equipment` takes these optional query parameters, which combine with AND:

| Parameter  | Meaning                                                              |
|------------|----------------------------------------------------------------------|
| `category` | `HEAVY_MACHINERY`, `AGRICULTURAL` or `EVENT_SETUP`                   |
| `location` | Case-insensitive "contains", e.g. `nakuru` matches `Nakuru Town`     |
| `minRate` / `maxRate` | `dailyRateKes` range, both ends included                  |
| `page`     | Zero-based page number (default `0`)                                 |
| `size`     | Page size, 1 to 100 (default `20`)                                   |
| `q`        | Keywords matched against name, location and description (any of them); short and common words are ignored |
| `availableFrom` / `availableTo` | ISO dates: only listings that take bookings (`AVAILABLE`) and have no `CONFIRMED`/`ACTIVE` booking on any of those days. One date alone means that day; at most 366 days |
| `sort`     | `dailyRateKes`, `createdAt` or `name`, optionally `,asc` or `,desc` (default `createdAt,desc`, newest first) |

`GET /equipment?category=AGRICULTURAL&location=nakuru&maxRate=8000&sort=dailyRateKes&page=0&size=2` returns a page (`PageResponse`):

```json
{
  "content": [ { "id": 3, "name": "Tractor A", "dailyRateKes": 5000, "...": "..." } ],
  "page": 0,
  "size": 2,
  "totalElements": 1,
  "totalPages": 1
}
```

Rows with the same sort value are ordered by `id`, so they don't move between pages.

Availability response (`EquipmentAvailabilityResponse`) for `GET /equipment/1/availability?from=2026-11-01&to=2026-11-30`. `available` is `false` when any day in the range is booked or the listing is under maintenance:

```json
{
  "equipmentId": 1,
  "from": "2026-11-01",
  "to": "2026-11-30",
  "available": false,
  "bookedPeriods": [
    { "startDate": "2026-11-10", "endDate": "2026-11-12" }
  ]
}
```

### Bookings: `/api/v1/bookings`

| Method | Path                       | Roles          | Description                                             |
|--------|----------------------------|----------------|---------------------------------------------------------|
| `POST` | `/bookings`                | CLIENT, ADMIN  | Book equipment (`201`). Both dates must be today or later, and both days count, so `startDate = endDate = today` books today. `409` if the listing is under maintenance or the dates are already booked. |
| `GET`  | `/bookings/my-bookings`    | CLIENT, ADMIN  | The caller's bookings, as a page. |
| `GET`  | `/bookings/for-my-equipment` | OWNER, ADMIN | Bookings on the caller's equipment, as a page, with who booked each one (`clientName`, and `clientPhone` while it is open). Optional `?status=PENDING` filter. |

Both booking lists take `page`, `size` (1 to 100, default 20) and `sort` (`startDate` or `creationDate`, optionally `,asc`/`,desc`; default `creationDate,desc`, newest first), and return the same `PageResponse` shape as the equipment list.
| `GET`  | `/bookings/{id}`           | Authenticated  | Get a booking. Only its client, the equipment owner or an admin may view it. The owner and admin also get the client's name and phone. |
| `PUT`  | `/bookings/{id}/confirm`   | OWNER, ADMIN   | Confirm a `PENDING` booking (equipment owner only).    |
| `PUT`  | `/bookings/{id}/complete`  | OWNER, ADMIN   | Complete a `CONFIRMED`/`ACTIVE` booking (equipment owner only). |
| `PUT`  | `/bookings/{id}/cancel`    | Authenticated  | Cancel a booking (its client or the equipment owner).  |

Create request (`CreateBookingRequest`):

```json
{
  "equipmentId": 1,
  "startDate": "2026-11-01",
  "endDate": "2026-11-03"
}
```

Response (`BookingResponse`). Three days at KES 25,000 per day:

```json
{
  "bookingId": 10,
  "equipmentId": 1,
  "clientId": 2,
  "startDate": "2026-11-01",
  "endDate": "2026-11-03",
  "totalCostKes": 75000,
  "status": "PENDING",
  "creationDate": "2026-10-26T08:05:00Z",
  "clientName": "Jane Wanjiru",
  "clientPhone": "+254712345678"
}
```

`clientName` and `clientPhone` are `null` unless the caller is the equipment's owner or an admin.

### Actuator

Spring Boot Actuator endpoints are served under `/actuator` (for example `/actuator/health`) without authentication.

### Example session with curl

```bash
# Register an owner and a client
curl -X POST localhost:8080/api/v1/auth/register -H "Content-Type: application/json" \
  -d '{"fullName":"Jane Owner","email":"owner@example.com","phoneNumber":"0712345678","password":"secret123","role":"OWNER"}'
curl -X POST localhost:8080/api/v1/auth/register -H "Content-Type: application/json" \
  -d '{"fullName":"John Client","email":"client@example.com","phoneNumber":"0798765432","password":"secret123","role":"CLIENT"}'

# Log in as the owner and create a listing
OWNER_TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"owner@example.com","password":"secret123"}' | jq -r .token)
curl -X POST localhost:8080/api/v1/equipment -H "Authorization: Bearer $OWNER_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Tractor","category":"AGRICULTURAL","dailyRateKes":8000,"location":"Nakuru"}'

# Log in as the client and book it
CLIENT_TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"client@example.com","password":"secret123"}' | jq -r .token)
curl -X POST localhost:8080/api/v1/bookings -H "Authorization: Bearer $CLIENT_TOKEN" -H "Content-Type: application/json" \
  -d '{"equipmentId":1,"startDate":"2026-11-01","endDate":"2026-11-03"}'

# The owner confirms it
curl -X PUT localhost:8080/api/v1/bookings/1/confirm -H "Authorization: Bearer $OWNER_TOKEN"
```

---

## Error Handling

`GlobalExceptionHandler` returns every error in the same shape (`ErrorResponse`):

```json
{
  "status": 400,
  "message": "Validation failed",
  "timestamp": "2026-10-26T08:00:00Z",
  "errors": {
    "email": "Invalid email format",
    "password": "Password must be at least 8 characters"
  }
}
```

- **Domain exceptions** extend `BaseException`, which carries its own HTTP status. Examples are `UserAlreadyExistsException` (409), `InvalidCredentialsException` (401), `EquipmentNotFoundException` (404) and the booking `NotFoundException` (404), `ConflictException` (409), `ForbiddenException` (403) and `ValidationException` (400).
- **Bean Validation failures** return `400`, with one entry per field in `errors`.
- **Invalid query or path parameters** return `400` with the problem in `message`: a value that can't be converted (`?status=FOO`, `?page=abc`), or one that isn't allowed (`size` above 100, an unknown `sort` field, `minRate` above `maxRate`).
- **Conflicting changes:** bookings carry a `version` (`@Version`, optimistic locking). If two requests change the same booking at once, for example a client cancelling while the owner confirms, the first one wins and the second gets `409` asking the caller to reload and try again, instead of silently overwriting it.
- **Missing, invalid or expired tokens** on a protected endpoint return `401` with a `WWW-Authenticate: Bearer` header (`RestAuthenticationEntryPoint`).
- **Authorisation failures** (a valid token without the required role, including `@PreAuthorize` checks) return `403`.
- **Anything else** returns `500` with the message `An unexpected error occurred: ...`.

---

## Frontend

The web app lives in [`frontend/`](frontend/). It is an Angular 22 app (standalone components, signals, no zone.js) styled with Angular Material.

It covers accounts, finding equipment, booking it, and the owner pages.

### Accounts

- **Register** (`/register`): the user picks "I want to hire equipment" (`CLIENT`) or "I have equipment to rent out" (`OWNER`). The phone number must be a Kenyan mobile number (`07XXXXXXXX` or `+2547XXXXXXXX`, the same rule as the API); spaces and dashes are allowed. The user is signed in right after.
- **No landing page, and no account needed to look around.** `/` opens the equipment list, and listings, their booked dates and the assistant all work signed out.
  - **Who needs an account:** clients sign in only to book, and owners to manage listings.
  - **Header:** signed-out visitors see a short intro above the listings and a **Log in** button.
- **Log in** (`/login`): afterwards the user goes back to the page that sent them there (`?returnUrl=`, only paths inside the app). The **Create an account** link keeps that return address too.
- **Demo owner** (portfolio deployments): under the login form, **Sign in as the demo owner** opens the owner side in one click (`POST /api/v1/auth/demo-owner`) on **Bookings on my equipment**. A guide strip across the top suggests what to try, with links:
  - answer the demo client's requests;
  - complete a hire;
  - edit a listing;
  - draft one with AI.

  The guide can be folded away (remembered in `localStorage`), and **Leave the demo** signs out. It only shows when the server reports demo data (`GET /api/v1/auth/demo`).
- **Session**: the JWT, user id, role and email (from the token's subject) are kept in `localStorage`, so a reload stays signed in. The session ends by itself when the token expires. The header shows the user and a **Log out** button.
- **Interceptor**: adds `Authorization: Bearer <token>` to requests for the backend only, never to other hosts. When the backend answers `401` to a signed-in request, it signs the user out and opens the login page.
- **Guards**: `guestGuard` (keeps signed-in users off login and register) and `roleGuard(...)` for the client and owner pages. `authGuard` (any signed-in user) is available but not needed by any route now.

### Finding equipment

- **List** (`/equipment`): cards with the name, category, location and daily rate, plus the status when a listing isn't taking bookings. The filters are keywords, category, location, a price range and the dates it must be free. You can sort (newest, price either way, name) and page through results (12, 24 or 48 per page).
  - **Filters in the URL** (`/equipment?category=AGRICULTURAL&location=Nakuru&sort=dailyRateKes,asc`): reloading, the back button and shared links keep the search. Values that aren't valid are ignored.
  - **Chips**: every active filter shows as a chip you can remove. A backwards price range is swapped.
  - **States**: loading (the previous results stay, dimmed), empty (with **Clear filters**) and error (with **Try again**).
- **Ask HireMe** (the hire assistant, above the filters): a chat that uses `POST /api/v1/equipment/ai/assistant`.
  - **Starting:** a few example questions get the user going. After each answer there are suggested follow-ups.
  - **Cards:** answers come with listing cards that show the rate and, when days are known, the total and whether the listing is free.
  - **Booking hand-off:** **Book these dates** opens the listing with those days already in the booking form (`/equipment/:id?from=…&to=…`).
  - **Memory:** the conversation is kept for the browser tab (sessionStorage), so going to a listing and back keeps it. **New conversation** clears it.
  - **When AI is off** (`GET /api/v1/ai/status`), the panel is hidden and the filters work as before.
- **Detail** (`/equipment/:id`): description, rate and status, plus a calendar of the next 12 months with confirmed bookings marked and listed (from `GET /api/v1/equipment/{id}/availability`). **Back to results** returns to the list with its filters. Clients book from here (see below).
- **Dates** are shown and typed day first (`15/11/2026`). Material's plain date adapter can't read that, so `core/day-first-dates.ts` parses it, and ISO dates too.

### For clients

- **Signed-out visitors** can pick dates on a listing and see the total. **Sign in to book** takes them to log in (or create an account), then back to the listing with the same dates, where the button becomes **Request booking**. The assistant's **Book these dates** works the same way.
- **Signed-in owners** see a note on the detail page instead of the form: clients book there.
- **Booking** (on the detail page, for listings that take bookings):
  - **Dates:** the client picks a range. Past days and days with a confirmed or active booking can't be picked, and a typed range that runs over a booked day is refused.
  - **Total:** shown before sending, as `days × daily rate` with both the first and last day counted, the same as the API. A single picked day is a one-day booking.
  - **Request booking:** sends `POST /api/v1/bookings` and opens **My bookings**. When the API refuses (for example `409` "Equipment is already booked for some of these dates" when the days were taken after the page loaded, or a `400` date error), its message shows.
- **My bookings** (`/my-bookings`, from `GET /api/v1/bookings/my-bookings`): newest request first, with the listing name, dates and number of days, total, request date and a status badge. **Cancel** shows only for pending or confirmed bookings, because an active one can only be cancelled by the owner. It asks first.
- **Booking detail** (`/my-bookings/:id`): the listing (linked), where it is, rate, dates, total, booking number, and what the status means in plain words, with **Cancel booking** where allowed. Someone else's booking (`403`) is shown like a missing one.

### For owners

These pages are open to `OWNER` and `ADMIN` only (`roleGuard`). The header shows **My listings** and **Bookings** to them.

- **My listings** (`/my-listings`, from `GET /api/v1/equipment/mine`): every listing the owner has, with its status.
  - **Edit:** opens the listing form.
  - **Pause bookings / Take bookings:** sets the status to `UNDER_MAINTENANCE` or `AVAILABLE`. Existing bookings stay.
  - **Delete:** asks first. When the API refuses because the listing has pending or upcoming bookings (`409`), its reason shows and the listing stays.
- **New and edit listing** (`/my-listings/new`, `/my-listings/:id/edit`):
  - **Validation:** name, category and location are required, and the daily rate must be above zero. The limits match the API.
  - **Partial updates:** an edit sends only the fields that changed (the API applies only what it receives), and saving with no changes sends nothing.
  - **Ownership:** another owner's listing can't be edited (the API refuses it anyway).
- **Write it for me:** with a name and category (plus a location for a rate suggestion, and optional details), `POST /api/v1/equipment/ai/describe` drafts a title and description. It also suggests a rate when there are similar listings to compare with, and says why. The owner copies the text, the rate, or both into the form and can still change them. Nothing is saved until **Create listing**. The section is hidden when AI is off, and it shows the API's message when AI is unavailable or the per-minute limit is reached.
- **Bookings on my equipment** (`/owner-bookings`, from `GET /api/v1/bookings/for-my-equipment`): earliest first, filtered by status through the URL (`?status=PENDING`). Each booking shows the listing, who booked it (with their phone number as a tap-to-call link while the booking is open), dates and number of days, total and request date. The buttons follow the API's rules:
  - **Pending:** Confirm (no question asked) or Decline. Confirming also declines the other pending requests for those days.
  - **Confirmed or Active:** Complete or Cancel.
  - **Completed or Cancelled:** nothing.

  Decline, Complete and Cancel ask first. When the API refuses an action (for example the booking changed meanwhile), its reason shows and the list reloads.

### Running it

```bash
cd frontend
npm ci
npm start                     # http://localhost:4200
```

The dev server sends `/api` to the backend on `http://localhost:8080` (`proxy.conf.json`), so the browser sees one origin. To call a backend on another origin instead, set `apiUrl` in `src/environments/environment.ts` and set that backend's `FRONTEND_URL` to the app's origin for CORS.

`docker compose up --build` also builds the app into an nginx image (`web`, on **http://localhost:8081**), which serves it and forwards `/api` to the `app` service.

| Command | What it does |
|---------|--------------|
| `npm start` | Dev server with live reload |
| `npm test` | Unit tests (Vitest); `npx ng test --watch=false` runs them once |
| `npm run lint` | ESLint, including the Angular template and accessibility rules |
| `npm run format:check` | Prettier check |
| `npm run build` | Production build in `dist/frontend/browser` |

### The API client

`src/app/api/` is generated from the backend's OpenAPI document (`openapi/hireme-api.json`) by OpenAPI Generator (`typescript-angular`, version pinned in `openapitools.json`). It holds one service per tag (`AuthService`, `EquipmentService`, `BookingsService`, `AIService`) and a TypeScript interface per DTO. Don't edit these files by hand. When the API changes:

```bash
npm run api:fetch                             # from a backend running on :8080
npm run api:fetch -- ../target/openapi.json   # or from the file ./mvnw test writes
npm run api:generate                          # needs Java on the PATH
```

Commit the updated `openapi/hireme-api.json` together with the regenerated client. The TypeScript compiler then flags any code that no longer matches the API.

---

## Getting Started

### Prerequisites

- JDK 25
- Docker and Docker Compose (for PostgreSQL, or for running the whole stack)
- Maven is optional; use the included wrapper (`./mvnw` or `mvnw.cmd`)

### 1. Configure environment variables

```bash
cp .env.example .env
```

Edit `.env`:

```dotenv
DB_USER=postgres
DB_PASSWORD=<choose a password>
JWT_SECRET=<a random string of at least 32 characters>
# Optional, see AI Features
HIREME_AI_ENABLED=false
GROQ_API_KEY=
```

HS256 needs a key of at least 256 bits, so `JWT_SECRET` must be at least 32 bytes long. One way to generate it is `openssl rand -hex 32`.

### 2a. Run everything with Docker Compose

```bash
docker compose up --build
```

This starts:

- `postgres`: PostgreSQL 16, database `equipment_hiring`, published on host port **5433**
- `app`: the API on **http://localhost:8080**, using the `docker` profile (`application-docker.yml`)
- `web`: the Angular app on **http://localhost:8081**, served by nginx, which forwards `/api` to `app` (see [Frontend](#frontend))

### 2b. Run the API locally against a Dockerised database

Start only the database:

```bash
docker compose up -d postgres
```

`application.properties` turns on the `dev` profile by default. The `application-dev.yml` file is git-ignored so local credentials stay out of the repo, so create `src/main/resources/application-dev.yml` yourself:

```yaml
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:postgresql://localhost:5433/equipment_hiring   # 5433 = docker compose host port
    username: postgres
    password: <your DB_PASSWORD>
  jpa:
    hibernate:
      ddl-auto: validate   # Flyway owns the schema
    show-sql: true

app:
  jwt:
    secret: <at least 32 characters>
    expiration-ms: 86400000
  frontend-url: http://localhost:4200
```

Then run:

```bash
./mvnw spring-boot:run        # macOS / Linux
mvnw.cmd spring-boot:run      # Windows
```

The API listens on `http://localhost:8080`.

### Build a JAR

```bash
./mvnw clean package
java -jar target/HireMe-0.0.1-SNAPSHOT.jar
```

---

## Configuration

| Property                 | Source / env var     | Default                  | Description                                   |
|--------------------------|----------------------|--------------------------|-----------------------------------------------|
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `dev`                | `dev` for local work, `docker` inside Compose |
| `spring.datasource.url`  | `DATABASE_URL` (docker profile) | n/a           | JDBC URL (on Neon, the direct host with `?sslmode=require`). Without it, the docker profile builds one from `DB_HOST`, `DB_PORT` (default `5432`) and `DB_NAME` |
| `spring.datasource.username` | `DB_USER`        | n/a                      | Database user                                 |
| `spring.datasource.password` | `DB_PASSWORD`    | n/a                      | Database password                             |
| `app.jwt.secret`         | `JWT_SECRET`         | n/a                      | HMAC signing key (at least 32 bytes)          |
| `app.jwt.expiration-ms`  | n/a                  | `86400000` (24 h)        | Token lifetime                                |
| `app.frontend-url`       | `FRONTEND_URL`       | `http://localhost:4200`  | The only origin CORS allows                   |
| `app.time-zone`          | n/a                  | `Africa/Nairobi`         | Business time zone: what "today" means for bookings |
| `app.bookings.activation-cron` | n/a            | `0 5 * * * *` (hourly)   | When confirmed bookings are checked for activation |
| `springdoc.api-docs.enabled` / `springdoc.swagger-ui.enabled` | n/a | `true` | Serve the OpenAPI spec and Swagger UI; set to `false` to turn them off |
| `hireme.ai.enabled`      | `HIREME_AI_ENABLED`  | `false`                  | Turn the AI features on (needs `GROQ_API_KEY`) |
| `hireme.ai.api-key`      | `GROQ_API_KEY`       | none                     | Groq API key; never commit it                  |
| `hireme.ai.model`        | `HIREME_AI_MODEL`    | `openai/gpt-oss-120b`    | Groq model id                                  |
| `hireme.ai.base-url`     | n/a                  | `https://api.groq.com/openai/v1` | OpenAI-compatible endpoint             |
| `hireme.ai.timeout` / `hireme.ai.max-retries` | n/a | `20s` / `1`           | Give up on a slow or failing Groq call         |
| `hireme.ai.requests-per-minute` | n/a           | `10`                     | AI calls per user per rolling minute, then `429` |
| `hireme.demo-listings.enabled` | `HIREME_DEMO_LISTINGS` | `false`     | Portfolio mode. See the demo data notes below |
| `hireme.demo-listings.owner-password` | `HIREME_DEMO_OWNER_PASSWORD` | none | Optional password for the demo owner (at least 8 characters). The one-click sign-in doesn't need it; without one, the owner gets a random password |

Demo data (`HIREME_DEMO_LISTINGS=true`), checked on every start:

- **Demo owner:** a demo owner (`demo-owner@hireme.example`) with 15 listings across all categories and towns. Any demo listing that's missing is added back; the ones still there are left as they are.
- **Demo client:** Wanjiku Kamau (`demo-client@hireme.example`), with booking requests on the demo listings dated from today: three pending and two confirmed. When she has no open booking left, a fresh set is made. Nobody can sign in as her.
- **One-click sign-in:** the demo owner can be signed into from the login page (`POST /api/v1/auth/demo-owner`).

Config files:

- `src/main/resources/application.properties`: shared defaults
- `src/main/resources/application-dev.yml`: local development (git-ignored, you create it)
- `src/main/resources/application-docker.yml`: used by Docker Compose and Render; reads everything from environment variables

---

## Testing

```bash
./mvnw test              # unit tests only (H2, no Docker needed)
./mvnw verify            # unit tests + integration tests (needs Docker) + JaCoCo report
./mvnw verify -DskipITs  # unit tests + JaCoCo, skip the integration tests
```

The JaCoCo report is written to `target/site/jacoco/index.html`.

The test suite covers:

- **Unit tests** for every service implementation, mapper, controller, the JWT provider and filter, the user-details service, the exception classes, `GlobalExceptionHandler` and the event records.
- **`ModularityTests`**, which checks the Spring Modulith module boundaries and fails the build on illegal cross-module access or dependency cycles.
- **`HireMeApplicationTests`**, which starts the full application on H2: Flyway runs the migrations and Hibernate validates every entity against the resulting schema, so a missing migration fails the build.
- **`BookingRepositoryTest`**, which runs the booking queries on H2, including the date-overlap edge cases (same day, back-to-back, containing).
- **`SecurityErrorResponsesTest`**, which calls the running app over HTTP to check the `401`/`403` responses.
- **`BookingConcurrentUpdateTest`**, which plays two overlapping changes to one booking step by step (a second transaction commits while the first still holds a stale copy) and checks that the stale one fails instead of overwriting.
- **`GroqWireTest`**, which runs the real Spring AI client against a fake OpenAI-compatible server started in the test. It checks the request we send to Groq (path, bearer key, model, messages), structured answers, and that a Groq error or a slow answer becomes `503` within the timeout. No real key or network is needed.
- **`OpenApiDocsTest`**, which fetches `/v3/api-docs` without a token and checks that every endpoint is there with the JWT scheme, real success codes (`201`, `204`), `ErrorResponse` error bodies and unique operation IDs. It also saves the spec to `target/openapi.json`.

Unit tests use H2 in memory with the settings in `src/test/resources/application.properties`.

### Integration tests (PostgreSQL via Testcontainers)

Classes ending in `IT` run in the `integration-test` phase (Maven Failsafe) against a real **PostgreSQL 16** started in Docker by [Testcontainers](https://testcontainers.com). `@ServiceConnection` points the app at it, and all `*IT` classes share one container per run. They start the whole app on a random port and call it over HTTP with real JWTs:

- **`BookingFlowIT`**:
  - **The main flow:** register, log in, list equipment, find it with filters, two overlapping requests, confirm (which cancels the clashing one), availability, booking the last and the following day, delete refused, then complete and `my-bookings`.
  - **`ACTIVE` bookings:** only the owner can cancel them.
- **`SecurityIT`**: `401` for a missing or bad token and a wrong password, `403` for the wrong role or someone else's listing, `409` for a duplicate email (a duplicate name is fine).
- **`PostgresSpecificIT`**:
  - **Migrations:** all of them ran, including the PostgreSQL-only `V2`.
  - **Check constraints:** they reject unknown statuses.
  - **Row lock:** a confirm **waits** while another connection holds the equipment row `FOR UPDATE`, and finishes once it is released.

They need Docker (Docker Desktop locally; GitHub Actions' Ubuntu runners have it), so CI runs them as part of `./mvnw clean verify`.

---

## CI/CD

GitHub Actions workflows live in `.github/workflows/`.

### `ci-pr.yml`: PR Quality & Security Gate

Runs on pull requests to `staging` and `main`:

1. **Secret scanning** with Gitleaks
2. **Dependency CVE scan** with Trivy (filesystem scan, `CRITICAL`/`HIGH`; results uploaded as SARIF)
3. **Build, unit and integration tests (PostgreSQL in Docker), modularity check, JaCoCo and the SonarCloud quality gate** (`./mvnw clean verify sonar:sonar` with pull-request decoration)

### `cd-pipeline.yml`: CI/CD Pipeline

Runs on pushes to `staging` and `main`:

1. **Build & analyse**: `verify` plus a SonarCloud branch analysis, then the JAR is packaged and uploaded as a build artifact
2. **Deploy to Staging** (on `staging`) or **Deploy to Production** (on `main`), each in a GitHub environment. The deploy steps are placeholders for now.

### `frontend.yml`: Frontend

Runs on pull requests and pushes to `staging` and `main` that touch `frontend/`. It runs `npm ci`, ESLint, the Prettier check, the unit tests and a production build on Node 24.

### Branching model

```
feature/*  ──PR──▶  staging  ──PR──▶  main
```

### Required secrets

| Secret        | Used for                       |
|---------------|--------------------------------|
| `SONAR_TOKEN` | SonarCloud analysis            |
| `GITHUB_TOKEN`| Provided by GitHub Actions automatically |

Static analysis is also set up for JetBrains Qodana (`qodana.yaml`, profile `qodana.starter`, JDK 25).

---

## Deployment

The database runs on [Neon](https://neon.tech), the API on [Render](https://render.com) (`render.yaml`, built from the root `Dockerfile`), and the Angular app on [Vercel](https://vercel.com) (`frontend/vercel.json`). Vercel forwards `/api/*` to Render, so the browser only talks to the Vercel address.

```
browser ──▶ Vercel (Angular app)  ──/api/*──▶  Render (hireme-api, Docker)  ──SSL──▶  Neon (PostgreSQL)
```

The step-by-step guide is in **[DEPLOYMENT.md](DEPLOYMENT.md)**. It covers the Neon connection settings (direct host, `sslmode=require`), the Render Blueprint and its variables, the Vercel project settings, a pre-launch checklist, an end-to-end check, troubleshooting, migrations and backups, and secret rotation.

---

## Project Structure

```
HireMe/
├── .github/workflows/        CI (PR gate) and CD pipelines
├── src/main/java/com/example/hireme/
│   ├── HireMeApplication.java
│   ├── user/                 AuthController, UserService, AuthService, DTOs, Role
│   │   └── internal/         User entity, repository, mapper, service impls, exceptions
│   ├── equipment/            EquipmentController, EquipmentService, DTOs, enums, events
│   │   └── internal/         Entity, repository, mapper, service impl, exceptions
│   ├── booking/              BookingController, BookingService, DTOs, BookingStatus, exceptions
│   │   └── internal/         Entity, repository, mapper, service impl
│   └── shared/               OPEN module
│       ├── event/            Booking integration events
│       ├── exception/        BaseException, ErrorResponse, GlobalExceptionHandler
│       └── security/         SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter
├── src/main/resources/       application.properties, application-docker.yml
├── src/test/                 Unit + modularity tests (H2)
├── frontend/                 Angular app (see Frontend)
│   ├── src/app/api/          Generated API client (npm run api:generate)
│   ├── src/app/core/auth/    AuthService (session), interceptor, guards
│   ├── src/app/pages/        Login, register, home
│   ├── src/app/features/equipment/  Equipment list (filters, AI search) and detail
│   ├── src/app/features/owner/      My listings, listing form ("Write it for me"), bookings on my equipment
│   ├── src/app/features/bookings/   Booking form, My bookings, booking detail, booking status rules
│   ├── openapi/              Copy of the backend's OpenAPI document
│   └── Dockerfile, nginx.conf
├── Dockerfile                Multi-stage build (Maven build → JRE runtime, non-root user)
├── docker-compose.yml        PostgreSQL + app + web
├── .env.example              Template for required environment variables
├── qodana.yaml               Qodana configuration
└── pom.xml
```

---

## License

Released under the [MIT License](LICENSE). © 2026 Hezron Okwach.
