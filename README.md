# CS202 Spring 2026 — Online Food Ordering System

A two-role (Customer / Restaurant Manager) online food-ordering platform.

- **Backend:** Java 17 + Spring Boot 3.2.5, exposing a REST API
- **Database:** MySQL 8.0, accessed with **raw JDBC / `PreparedStatement` — no ORM**
- **Client:** JavaFX 21 desktop application
- **Architecture:** three layers — Presentation (JavaFX) -> Controller -> Service -> DAO -> MySQL

The Spring Boot backend and MySQL run together (via Docker); the JavaFX client runs
on your machine and talks to the backend over HTTP at `http://localhost:8080`.

---

## Prerequisites

| Tool | Version | Needed for |
|------|---------|------------|
| **Java JDK** | **17** (exactly 17 is enforced for the build/tests) | Building & running |
| **Maven** | 3.8+ | Building, running the UI/backend |
| **Docker** + Docker Desktop | recent | Running the backend + database (recommended path) |
| **GNU Make** | 3.81+ | Optional — shorthand for the commands below |

> A local **MySQL 8** install is only needed for the *manual (no-Docker)* path in the last section.

---

## Quick Start (Docker — recommended)

```bash
# 1. One-time: copy the config template
cp .env-example .env

# 2. Start MySQL + backend (wipes the DB, loads DDL.sql then DML.sql, starts the API on :8080)
make run-fresh

# 3. In a second terminal, launch the JavaFX client
make ui
```

> **Maximise the UI window after it opens.** The JavaFX layout has a minimum width;
> on smaller windows panels can be clipped. Use the maximise button or drag to full size.

### Without Make (plain Docker + Maven)

`make` just wraps these commands — run them directly if you don't have Make:

```bash
cp .env-example .env

# Start database + backend (builds images, runs in background)
docker compose --profile run up --build -d

# Launch the JavaFX client (needs Maven + a local JDK 17)
mvn javafx:run
```

Stop everything:

```bash
docker compose --profile run down      # stop, keep the database volume
docker compose --profile run down -v   # stop AND wipe the database
```

The database is seeded automatically on first start: Docker loads `DDL.sql`
(schema) then `DML.sql` (sample data) from `docker-entrypoint-initdb.d`.

---

## Test Credentials

All seed accounts use the password shown. A user's city comes from their selected
`UserAddress` (the `Users` table has no `city` column), which controls which
restaurants they can browse and order from.

| Username | Password | Role | City |
|---|---|---|---|
| `manager_ali` | `ali2026` | MANAGER | Istanbul |
| `manager_ayse` | `ayse2026` | MANAGER | Ankara |
| `manager_mehmet` | `mehmet2026` | MANAGER | Istanbul |
| `customer_ece` | `ece2026` | CUSTOMER | Istanbul |
| `customer_berk` | `berk2026` | CUSTOMER | Istanbul |
| `customer_selin` | `selin2026` | CUSTOMER | Ankara |
| `customer_can` | `can2026` | CUSTOMER | Istanbul |
| `customer_pinar` | `pinar2026` | CUSTOMER | Ankara |

Passwords are stored as salted PBKDF2-HMAC-SHA256 hashes (100k iterations); the
plaintext above is only for testing.

---

## Running the Tests

```bash
make test-fresh    # spins up a clean MySQL container and runs the full suite in Docker
```

Without Make:

```bash
docker compose --profile test up --build --exit-code-from test
```

If the backend + database are already running (after `make run-fresh`), you can run
the suite against them directly:

```bash
mvn test -P backend
```

---

## Manual Setup (no Docker)

Use this only if you cannot use Docker. It needs a **local MySQL 8** server running on
`localhost:3306`.

```bash
# 1. Load the schema and seed data (run as a MySQL admin user).
#    DDL.sql creates the `food_ordering` database and all tables.
mysql -u root -p < DDL.sql
mysql -u root -p < DML.sql

# 2. Create the application DB user the backend expects (matches application.properties).
mysql -u root -p -e "CREATE USER IF NOT EXISTS 'fooduser'@'localhost' IDENTIFIED BY 'foodpass'; GRANT ALL PRIVILEGES ON food_ordering.* TO 'fooduser'@'localhost'; FLUSH PRIVILEGES;"

# 3. Start the backend (reads the localhost:3306 defaults from application.properties).
mvn spring-boot:run -P backend

# 4. In a second terminal, start the JavaFX client.
mvn javafx:run
```

Default connection settings (in `src/main/resources/application.properties`):
`jdbc:mysql://localhost:3306/food_ordering`, user `fooduser`, password `foodpass`.
Override them with the `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` environment variables
if your local MySQL differs.

---

## Project Structure

```
.
├── DDL.sql              # database schema (11 tables, keys, constraints)
├── DML.sql              # sample/seed data
├── pom.xml              # Maven build (Java 17, Spring Boot 3.2.5, JavaFX 21)
├── Dockerfile           # backend container image
├── docker-compose.yml   # MySQL + backend (+ test) services
├── Makefile             # convenience targets (run-fresh, ui, test-fresh, down, ...)
├── .env-example         # configuration template (copy to .env)
├── img/                 # menu-item images referenced by DML.sql
├── scripts/             # developer utilities
├── docs/                # detailed developer documentation (see Further Reading)
└── src/
    ├── main/java/org/example/
    │   ├── controller/  # REST controllers
    │   ├── service/     # business logic, authorization, transactions
    │   ├── dao/         # JDBC data access — all hand-written SQL
    │   ├── model/       # domain classes
    │   └── ui/          # JavaFX desktop client
    └── test/java/org/example/   # unit, integration, and HTTP tests
```

---

## Further Reading

This README alone is enough to build, run, and test the project. For deeper detail,
see the `docs/` directory:

| Document | Topic |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design, 3-tier architecture, full request flow |
| [docs/DATABASE.md](docs/DATABASE.md) | Schema (11 tables), key queries, indexes |
| [docs/MODELS.md](docs/MODELS.md) | Domain classes and their relationships |
| [docs/SERVICES.md](docs/SERVICES.md) | Service layer: business logic, authorization, transactions |
| [docs/API_ENDPOINTS.md](docs/API_ENDPOINTS.md) | REST API: endpoints, request/response JSON, examples |
| [docs/UI_COMPONENTS.md](docs/UI_COMPONENTS.md) | JavaFX views, navigation, event handling |
| [docs/SETUP.md](docs/SETUP.md) | Extended setup guide and Make targets |
| [docs/DEMO.md](docs/DEMO.md) | Step-by-step demo walkthrough (customer + manager flows) |
| [docs/DEBUGGING.md](docs/DEBUGGING.md) | Common issues and troubleshooting |
