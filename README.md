# HireMe

HireMe is a REST API for hiring out equipment. Equipment owners list machinery (heavy machinery, agricultural, event setup) with a daily rate in Kenyan Shillings. Clients browse the listings and book equipment for a date range, and the owner confirms, completes or cancels the booking.

It is a Spring Boot **modular monolith**: one deployable application split into modules (`user`, `equipment`, `booking`, `shared`). Spring Modulith enforces the module boundaries and carries events between modules.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Domain Model](#domain-model)
- [Booking Lifecycle](#booking-lifecycle)
- [Security](#security)
- [API Reference](#api-reference)
- [Error Handling](#error-handling)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Testing](#testing)
- [CI/CD](#cicd)
- [Project Structure](#project-structure)
- [Known Limitations](#known-limitations)
- [License](#license)

---

## Tech Stack

| Concern            | Technology                                              |
|--------------------|---------------------------------------------------------|
| Language           | Java 25                                                 |
| Framework          | Spring Boot 4.1 (Web, Data JPA, Validation, Security, Actuator) |
| Modularity         | Spring Modulith 2.1 (core, JPA event publication registry) |
| Database           | PostgreSQL 16 (H2 in tests)                             |
| Authentication     | JWT (jjwt 0.12) and BCrypt password hashing             |
| Boilerplate        | Lombok                                                  |
| Build              | Maven (wrapper included)                                |
| Quality            | JaCoCo, SonarCloud, Qodana, Gitleaks, Trivy             |
| Containerisation   | Docker multi-stage build and Docker Compose             |

---

## Architecture

```
com.example.hireme
├── user/        Registration, login, user lookup         (public API: UserService, AuthService, DTOs, Role)
├── equipment/   Equipment listings and availability      (public API: EquipmentService, DTOs, enums)
├── booking/     Bookings and their lifecycle             (public API: BookingService, DTOs, BookingStatus)
└── shared/      OPEN module: security, exceptions, integration events
```

Each module exposes a small public API in its root package (service interfaces, request/response records and enums). Entities, repositories, mappers and service implementations live in an `internal` sub-package that other modules cannot reach. `ModularityTests` runs `ApplicationModules.verify()` to fail the build if a module reaches into another module's internals or if modules depend on each other in a cycle.

### Module dependencies

```
booking ──calls──▶ equipment ──calls──▶ user
   │                   ▲
   └── publishes ──────┘ (events via shared.event)
```

- **booking → equipment (synchronous):** to check availability, read the daily rate and find the equipment's owner.
- **equipment → user (synchronous):** to check whether the caller has the `OWNER`/`ADMIN` role.
- **booking → equipment (asynchronous):** `BookingCreatedEvent`, `BookingCancelledEvent` and `BookingCompletedEvent` are published by `booking` and handled in `equipment` with `@ApplicationModuleListener`.

The booking events live in `shared.event`, not in `booking`. If they lived in `booking`, `equipment` would depend on `booking` while `booking` already depends on `equipment`, which is a cycle Modulith rejects. `shared` is declared an **open** module (`package-info.java`), so every module can use its contents.

Because `spring-modulith-starter-jpa` is on the classpath, published events are recorded in an event publication table. The listeners run asynchronously after the publishing transaction commits.

### Database schemas

Each module owns its own PostgreSQL schema. Hibernate creates the schemas (`hbm2ddl.create_namespaces: true`) and the tables (`ddl-auto: update`):

| Module    | Schema          | Table       |
|-----------|-----------------|-------------|
| user      | `app_user`      | `users`     |
| equipment | `app_equipment` | `equipment` |
| booking   | `app_booking`   | `bookings`  |

Modules refer to each other's records by ID only (`ownerId`, `equipmentId`, `clientId`). There are no foreign keys or JPA relationships between modules.

---

## Domain Model

### User

| Field         | Notes                                                    |
|---------------|----------------------------------------------------------|
| `id`          | Generated                                                |
| `fullName`    | Required, unique                                         |
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
| `description`  | Optional                                           |
| `dailyRateKes` | Required, must be greater than zero                |
| `location`     | Required                                           |
| `status`       | `AVAILABLE` (default), `HIRED`, `UNDER_MAINTENANCE`|
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

---

## Booking Lifecycle

```
             create (client)
                  │
                  ▼
             ┌─────────┐   confirm (owner)   ┌───────────┐   complete (owner)   ┌───────────┐
             │ PENDING │ ──────────────────▶ │ CONFIRMED │ ───────────────────▶ │ COMPLETED │
             └─────────┘                     └───────────┘                      └───────────┘
                  │                                │
                  │ cancel (client or owner)       │ cancel (client or owner)
                  ▼                                ▼
             ┌───────────┐ ◀───────────────────────┘
             │ CANCELLED │
             └───────────┘
```

| Action   | Who                                   | Allowed from                | Side effect on equipment            |
|----------|---------------------------------------|-----------------------------|-------------------------------------|
| Create   | Any `CLIENT`/`ADMIN`; equipment must be `AVAILABLE` | n/a                | `BookingCreatedEvent` sets it to `HIRED` |
| Confirm  | Owner of the equipment                | `PENDING`                   | none                                |
| Complete | Owner of the equipment                | `CONFIRMED` or `ACTIVE`     | `BookingCompletedEvent` sets it to `AVAILABLE` |
| Cancel   | The booking's client or the equipment owner | anything except `COMPLETED`/`CANCELLED` | `BookingCancelledEvent` sets it to `AVAILABLE` |

`ACTIVE` is defined in `BookingStatus` but nothing in the code sets it yet.

---

## Security

- **Stateless JWT authentication.** `POST /api/v1/auth/login` returns a signed HS256 token. The subject is the user's email and a `role` claim holds the role. Tokens expire after 24 hours (`app.jwt.expiration-ms`).
- Send the token on every protected request:
  ```
  Authorization: Bearer <token>
  ```
- `JwtAuthenticationFilter` validates the token, loads the user through `CustomUserDetailsService` and fills in the security context. Roles become authorities with a `ROLE_` prefix (`ROLE_CLIENT`, `ROLE_OWNER`, `ROLE_ADMIN`).
- **Authorisation** uses `@PreAuthorize` on each endpoint (method security is enabled). Ownership checks, such as "is this your equipment?", happen in the service layer.
- **Public routes:** `/api/v1/auth/**` and `/actuator/**`. Every other route needs a valid token.
- **CORS** allows only `app.frontend-url` (default `http://localhost:5173`), with credentials, for `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- **CSRF** uses a cookie-based token repository (`XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header) for non-API routes. It is turned off for `/api/v1/**` and `/actuator/**` because those requests use bearer tokens, not cookies.
- Passwords are hashed with BCrypt.

---

## API Reference

Base path: `/api/v1`. Request and response bodies are JSON.

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
| `GET`    | `/equipment`      | Authenticated  | List all equipment. Optional `?category=HEAVY_MACHINERY` filter. |
| `GET`    | `/equipment/{id}` | Authenticated  | Get one item (`404` if it does not exist).        |
| `PUT`    | `/equipment/{id}` | OWNER, ADMIN   | Update a listing. Refused (`403`) while it is `HIRED`. |
| `DELETE` | `/equipment/{id}` | OWNER, ADMIN   | Delete a listing (`204`). Refused (`403`) while it is `HIRED`. |

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

The update request (`UpdateEquipmentRequest`) has the same fields, and none of them are required. Response (`EquipmentResponse`):

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

### Bookings: `/api/v1/bookings`

| Method | Path                       | Roles          | Description                                             |
|--------|----------------------------|----------------|---------------------------------------------------------|
| `POST` | `/bookings`                | CLIENT, ADMIN  | Book equipment (`201`). Equipment must be `AVAILABLE`.  |
| `GET`  | `/bookings/my-bookings`    | CLIENT, ADMIN  | List the caller's bookings.                             |
| `GET`  | `/bookings/{id}`           | Authenticated  | Get a booking. Only its client or the equipment owner may view it. |
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
  "creationDate": "2026-10-26T08:05:00Z"
}
```

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

- **Domain exceptions** extend `BaseException`, which carries its own HTTP status. Examples are `UserAlreadyExistsException` (409), `InvalidCredentialsException` (401), `EquipmentNotFoundException` (404) and the booking `NotFoundException` (404) and `ConflictException` (409).
- **Bean Validation failures** return `400`, with one entry per field in `errors`.
- **Anything else** returns `500` with the message `An unexpected error occurred: ...`.

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
```

HS256 needs a key of at least 256 bits, so `JWT_SECRET` must be at least 32 bytes long. One way to generate it is `openssl rand -hex 32`.

### 2a. Run everything with Docker Compose

```bash
docker compose up --build
```

This starts:

- `postgres`: PostgreSQL 16, database `equipment_hiring`, published on host port **5433**
- `app`: the API on **http://localhost:8080**, using the `docker` profile (`application-docker.yml`)

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
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        hbm2ddl:
          create_namespaces: true

app:
  jwt:
    secret: <at least 32 characters>
    expiration-ms: 86400000
  frontend-url: http://localhost:5173
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
| `spring.datasource.url`  | `DATABASE_URL` (docker profile) | n/a           | JDBC URL                                      |
| `spring.datasource.username` | `DB_USER`        | n/a                      | Database user                                 |
| `spring.datasource.password` | `DB_PASSWORD`    | n/a                      | Database password                             |
| `app.jwt.secret`         | `JWT_SECRET`         | n/a                      | HMAC signing key (at least 32 bytes)          |
| `app.jwt.expiration-ms`  | n/a                  | `86400000` (24 h)        | Token lifetime                                |
| `app.frontend-url`       | n/a                  | `http://localhost:5173`  | The only origin CORS allows                   |

Config files:

- `src/main/resources/application.properties`: shared defaults
- `src/main/resources/application-dev.yml`: local development (git-ignored, you create it)
- `src/main/resources/application-docker.yml`: used by Docker Compose; reads everything from environment variables

---

## Testing

```bash
./mvnw test       # unit tests
./mvnw verify     # tests + JaCoCo coverage report
```

The JaCoCo report is written to `target/site/jacoco/index.html`.

The test suite covers:

- **Unit tests** for every service implementation, mapper, controller, the JWT provider and filter, the user-details service, the exception classes, `GlobalExceptionHandler` and the event records.
- **`ModularityTests`**, which checks the Spring Modulith module boundaries and fails the build on illegal cross-module access or dependency cycles.
- **`HireMeApplicationTests`** (full context load), which is `@Disabled` by default because it needs a running PostgreSQL.

Tests use H2 in memory with the settings in `src/test/resources/application.properties`.

---

## CI/CD

GitHub Actions workflows live in `.github/workflows/`.

### `ci-pr.yml`: PR Quality & Security Gate

Runs on pull requests to `staging` and `main`:

1. **Secret scanning** with Gitleaks
2. **Dependency CVE scan** with Trivy (filesystem scan, `CRITICAL`/`HIGH`; results uploaded as SARIF)
3. **Build, tests, modularity check, JaCoCo and the SonarCloud quality gate** (`./mvnw clean verify sonar:sonar` with pull-request decoration)

### `cd-pipeline.yml`: CI/CD Pipeline

Runs on pushes to `staging` and `main`:

1. **Build & analyse**: `verify` plus a SonarCloud branch analysis, then the JAR is packaged and uploaded as a build artifact
2. **Deploy to Staging** (on `staging`) or **Deploy to Production** (on `main`), each in a GitHub environment. The deploy steps are placeholders for now.

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
├── Dockerfile                Multi-stage build (Maven build → JRE runtime, non-root user)
├── docker-compose.yml        PostgreSQL + app
├── .env.example              Template for required environment variables
├── qodana.yaml               Qodana configuration
└── pom.xml
```

---

## Known Limitations

These are behaviours of the current code that you should know about when you work on or consume the API:

- **Docker image JDK version:** the `Dockerfile` builds and runs on Eclipse Temurin **21**, but `pom.xml` targets Java **25**. The images need to move to a JDK 25 base for the container build to compile.
- **Equipment ownership on update/delete:** the service checks that the caller has the `OWNER`/`ADMIN` role, but not that the caller owns *that* listing.
- **Full replacement on update:** `PUT /equipment/{id}` overwrites `name`, `description`, `dailyRateKes` and `location` with the values sent, so a field left out becomes `null`. `category` is not updated.
- **Equipment is reserved when the booking is created:** the equipment becomes `HIRED` as soon as a booking is created (while it is `PENDING`), not when the owner confirms it. Availability is a single status flag and does not look at date ranges.
- **`ACTIVE` booking status** exists but is never set.
- **Booking error codes:** in the booking module, `ForbiddenException` and `ValidationException` return `409 Conflict` instead of `403` and `400`.
- **Authorisation failures from `@PreAuthorize`** are caught by the catch-all handler and come back as `500` instead of `403`.
- **`fullName` is unique** in the `users` table, so two users cannot share a name.

---

## License

Released under the [MIT License](LICENSE). © 2026 Hezron Okwach.
