.PHONY: build clean run run-fresh ui logs tail-logs test test-fresh down down-v

# Default Maven path
MVN ?= mvn

# Build the project locally (skips tests)
build:
	$(MVN) clean package -DskipTests

# Clean build artifacts
clean:
	$(MVN) clean
	rm -rf logs/

# Launch JavaFX UI (backend must already be running)
ui:
	$(MVN) javafx:run

# Follow backend logs from Docker
logs:
	docker compose --profile run logs -f backend

# Tail backend structured log from Docker volume
tail-logs:
	docker compose --profile run exec backend tail -f /app/logs/app.log

# Start DB + backend (keep volumes)
run:
	docker compose --profile run build
	docker compose --profile run up -d

# Wipe volumes, then start DB + backend
run-fresh:
	docker compose --profile run down -v
	docker compose --profile run up --build -d

# Start DB + run tests (keep volumes)
test:
	docker compose --profile test up --build --exit-code-from test

# Wipe volumes, then start DB + run tests
test-fresh:
	docker compose --profile test down -v
	docker compose --profile test up --build --exit-code-from test

# Stop all services (keep volumes)
down:
	docker compose --profile run --profile test down

# Stop all services and delete volumes
down-v:
	docker compose --profile run --profile test down -v
