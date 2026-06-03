# Setup Guide

## Prerequisites

- **Java 17+** — tests require exactly JDK 17 (see [TROUBLESHOOTING.md §1](TROUBLESHOOTING.md#1-jdk-version-drift))
- **Maven 3.8+**
- **Docker & Docker Desktop** (required for backend and database)
- **GNU Make 3.81+** (optional; all targets have manual equivalents below)

## Configuration

```bash
cp .env-example .env   # one-time setup; edit if you need different ports/credentials
```

---

## Why the backend and DB are containerised but the UI is not

This mirrors how the system would be deployed in production. The database and
REST API live on a server — containerised so they're isolated, reproducible, and
independent of whatever the host machine has installed. The JavaFX client runs
on the user's laptop, the same way any desktop app would. In a real deployment
you wouldn't ship the source at all — just a build artifact (a fat JAR or an
installer) and the user would never know or care what's running on the server.

For this project the source is all in one repo for convenience, but the
`Makefile` keeps the boundary clean: `make run-fresh` starts the server side,
`make ui` starts the client side. They communicate over HTTP on `localhost:8080`
exactly as they would over a network in production.

---

## Docker (recommended)

### Start backend + database

Seeds DDL + DML, starts the REST API on `:8080`.

```bash
make run-fresh     # wipe DB volume first — guaranteed clean seed state
make run           # keep existing data
```

Without make:
```bash
docker compose --profile run up --build
```

Check the backend is up:
```bash
make logs
# or: docker compose --profile run logs -f backend
```

### Launch the UI

In a separate terminal (after backend is healthy at `http://localhost:8080`):

```bash
make ui
# or: mvn javafx:run
```

### Reset the database

```bash
make run-fresh     # wipes DB volume, reseeds, restarts backend
```

### Run all tests

```bash
make test-fresh    # spins up a clean MySQL container, runs the full suite
make test          # run against an already-running DB (e.g. after make run-fresh)
```

Expected: `BUILD SUCCESS, Tests run: 237, Failures: 0, Errors: 0`

### Stop services

```bash
make down          # stop containers, keep DB volume
make down-v        # stop and wipe DB volumes
```

### Tail backend logs

```bash
make tail-logs     # exec into the backend container and tail /app/logs/app.log
make logs          # Docker Compose log stream
```

---

## Manual Setup (no Docker)

1. Install and start **MySQL 8.0** locally.
2. Initialise the schema and seed data:
   ```sql
   source DDL.sql
   source DML.sql
   ```
3. Confirm credentials in `src/main/resources/application.properties` match your MySQL setup.
4. Start the backend:
   ```bash
   mvn spring-boot:run -P backend
   ```
5. In a separate terminal, launch the UI:
   ```bash
   mvn javafx:run
   ```

---

## Maven profiles

| Profile | Default? | What it builds |
|---|---|---|
| `ui` | ✅ yes | JavaFX + Spring; entry point `Main.java` |
| `backend` | no | Excludes `ui/` and `Main.java`; entry point `BackendApplication.java` |

Tests always run under `-P backend` (the Docker test image uses this profile).

---

## Project structure

```
src/main/java/org/example/
├── BackendApplication.java    # headless backend entry point
├── Main.java                  # JavaFX + Spring entry point (default profile)
├── model/                     # entity classes
├── dao/                       # raw JDBC data access — every SQL hand-written
├── service/                   # business logic + @Transactional boundaries
├── controller/                # REST controllers + GlobalExceptionHandler + ControllerInputs
└── ui/                        # JavaFX views (18 view classes)

img/                           # placeholder JPGs for DML menu item imagePaths
scripts/                       # reproducibility helpers (api.sh, GenerateSeedHashes.java)
docs/                          # all documentation (setup, demo, QA, SQL, report draft)
DDL.sql                        # schema — run this first
DML.sql                        # seed data
```

---

## REST API (port 8080)

Full reference with request fields, response shapes, and error codes: **[misc/API.md](misc/API.md)**

Quick endpoint index:

| Method | Path | Who |
|--------|------|-----|
| POST | /api/auth/login | anyone |
| POST | /api/auth/register | anyone |
| GET  | /api/restaurants?city=&keyword= | customer |
| GET/POST | /api/restaurants | customer/manager |
| PUT  | /api/restaurants/{id} | manager (owner) |
| GET/POST/PUT/DELETE | /api/restaurants/{id}/menu | manager (owner) |
| GET/POST/DELETE | /api/restaurants/{id}/coupons | manager (owner) |
| GET  | /api/restaurants/{id}/ratings | manager |
| POST | /api/orders | customer |
| PUT  | /api/orders/{id}/accept | manager (SENT→PREPARING) |
| PUT  | /api/orders/{id}/arrive | manager (PREPARING→ARRIVED) |
| POST | /api/orders/{id}/rate | customer |
| GET/POST/DELETE/PUT | /api/users/{userId}/addresses | customer |
| GET/POST/DELETE | /api/users/{userId}/phones | customer |
| GET  | /api/statistics/restaurant/{id}/monthly | manager |
