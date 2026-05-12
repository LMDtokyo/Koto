.PHONY: all build up stack stack-health down logs migrate migrate-host lint test proto clean prod-up prod-down prod-logs prod-migrate

# ─── Config ───────────────────────────────────────────────────────────────────
SERVICES := gateway auth chat user notification media bot
GO       := go
DOCKER   := docker compose
# Read DB user from .env so `make migrate` works without manually exporting variables.
POSTGRES_USER_VAL := $(shell sed -n 's/^POSTGRES_USER=//p' .env 2>/dev/null | tr -d '\r')
ifeq ($(strip $(POSTGRES_USER_VAL)),)
POSTGRES_USER_VAL := koto
endif
POSTGRES_PASSWORD_VAL := $(shell sed -n 's/^POSTGRES_PASSWORD=//p' .env 2>/dev/null | tr -d '\r')
ifeq ($(strip $(POSTGRES_PASSWORD_VAL)),)
POSTGRES_PASSWORD_VAL := change_me_in_prod
endif
# Host port published in docker-compose.yml (postgres:5432 → host)
POSTGRES_HOST_PORT ?= 5433

# ─── Top-level ────────────────────────────────────────────────────────────────
all: build

## build: compile all services
build:
	@for svc in $(SERVICES); do \
		echo "→ building $$svc..."; \
		cd services/$$svc && $(GO) build -ldflags="-s -w" -o ../../bin/$$svc ./cmd/server && cd ../..; \
	done

## up: start all services with Docker Compose
up:
	$(DOCKER) up -d --build

## stack: up + wait for Postgres + migrations + gateway /health (удобно агенту и разработчику)
stack: up
	@echo "→ waiting for postgres (pg_isready)…"
	@i=0; while [ $$i -lt 60 ]; do \
	  $(DOCKER) exec -T postgres pg_isready -U $(POSTGRES_USER_VAL) -d koto >/dev/null 2>&1 && break; \
	  i=$$((i+1)); sleep 2; \
	done; \
	$(DOCKER) exec -T postgres pg_isready -U $(POSTGRES_USER_VAL) -d koto >/dev/null 2>&1 || (echo "postgres not ready in time"; exit 1)
	@echo "→ waiting for scylladb (cqlsh, best effort)…"
	@i=0; while [ $$i -lt 40 ]; do \
	  $(DOCKER) exec -T scylladb cqlsh -e "DESCRIBE KEYSPACES" >/dev/null 2>&1 && break; \
	  i=$$((i+1)); sleep 3; \
	done; true
	$(MAKE) migrate
	$(MAKE) stack-health

## stack-health: GET gateway /health; при сбое — один restart auth+gateway и повтор
stack-health:
	@curl -sf http://127.0.0.1:8081/health >/dev/null && echo "✓ gateway http://127.0.0.1:8081/health" && exit 0; \
	echo "! /health failed, restarting auth gateway…"; \
	$(DOCKER) restart auth gateway 2>/dev/null || true; \
	sleep 3; \
	curl -sf http://127.0.0.1:8081/health >/dev/null && echo "✓ gateway /health ok after restart" || (echo "! gateway still unhealthy — см. docker compose logs gateway"; exit 1)

## dev: start only infrastructure (db, cache, nats, minio)
dev:
	$(DOCKER) up -d postgres scylladb dragonfly nats minio

## down: stop all services
down:
	$(DOCKER) down

## logs: follow logs for all services
logs:
	$(DOCKER) logs -f

## logs-svc: follow logs for specific service (make logs-svc SVC=auth)
logs-svc:
	$(DOCKER) logs -f $(SVC)

## restart-svc: restart a specific service (make restart-svc SVC=chat)
restart-svc:
	$(DOCKER) restart $(SVC)

# ─── Database ─────────────────────────────────────────────────────────────────
## migrate: run all database migrations
migrate:
	@echo "→ running postgres migrations..."
	@$(DOCKER) exec -T postgres psql -U $(POSTGRES_USER_VAL) -d koto -f /dev/stdin < services/auth/migrations/001_accounts.sql
	@$(DOCKER) exec -T postgres psql -U $(POSTGRES_USER_VAL) -d koto -f /dev/stdin < services/auth/migrations/002_sessions.sql
	@$(DOCKER) exec -T postgres psql -U $(POSTGRES_USER_VAL) -d koto -f /dev/stdin < services/user/migrations/001_users.sql
	@$(DOCKER) exec -T postgres psql -U $(POSTGRES_USER_VAL) -d koto -f /dev/stdin < services/user/migrations/002_prekeys.sql
	@$(DOCKER) exec -T postgres psql -U $(POSTGRES_USER_VAL) -d koto -f /dev/stdin < services/user/migrations/003_username.sql
	@$(DOCKER) exec -T postgres psql -U $(POSTGRES_USER_VAL) -d koto -f /dev/stdin < services/user/migrations/004_banner.sql
	@$(DOCKER) exec -T postgres psql -U $(POSTGRES_USER_VAL) -d koto -f /dev/stdin < services/user/migrations/005_friend_requests.sql
	@$(DOCKER) exec -T postgres psql -U $(POSTGRES_USER_VAL) -d koto -f /dev/stdin < services/notification/migrations/001_devices.sql
	@$(DOCKER) exec -T postgres psql -U $(POSTGRES_USER_VAL) -d koto -f /dev/stdin < services/media/migrations/001_files.sql
	@$(DOCKER) exec -T postgres psql -U $(POSTGRES_USER_VAL) -d koto -f /dev/stdin < services/media/migrations/002_is_public.sql
	@$(DOCKER) exec -T postgres psql -U $(POSTGRES_USER_VAL) -d koto -f /dev/stdin < services/bot/migrations/001_bots.sql
	@echo "→ running scylla migrations..."
	@$(DOCKER) exec -T scylladb cqlsh < services/chat/migrations/001_messages.cql
	@echo "✓ migrations complete"

## migrate-host: Postgres + Scylla migrations without `docker exec` (uses psql on localhost and cqlsh if present)
migrate-host:
	@echo "→ postgres @ 127.0.0.1:$(POSTGRES_HOST_PORT) …"
	@PGPASSWORD=$(POSTGRES_PASSWORD_VAL) psql -h 127.0.0.1 -p $(POSTGRES_HOST_PORT) -U $(POSTGRES_USER_VAL) -d koto -v ON_ERROR_STOP=1 -f services/auth/migrations/001_accounts.sql
	@PGPASSWORD=$(POSTGRES_PASSWORD_VAL) psql -h 127.0.0.1 -p $(POSTGRES_HOST_PORT) -U $(POSTGRES_USER_VAL) -d koto -v ON_ERROR_STOP=1 -f services/auth/migrations/002_sessions.sql
	@PGPASSWORD=$(POSTGRES_PASSWORD_VAL) psql -h 127.0.0.1 -p $(POSTGRES_HOST_PORT) -U $(POSTGRES_USER_VAL) -d koto -v ON_ERROR_STOP=1 -f services/user/migrations/001_users.sql
	@PGPASSWORD=$(POSTGRES_PASSWORD_VAL) psql -h 127.0.0.1 -p $(POSTGRES_HOST_PORT) -U $(POSTGRES_USER_VAL) -d koto -v ON_ERROR_STOP=1 -f services/user/migrations/002_prekeys.sql
	@PGPASSWORD=$(POSTGRES_PASSWORD_VAL) psql -h 127.0.0.1 -p $(POSTGRES_HOST_PORT) -U $(POSTGRES_USER_VAL) -d koto -v ON_ERROR_STOP=1 -f services/user/migrations/003_username.sql
	@PGPASSWORD=$(POSTGRES_PASSWORD_VAL) psql -h 127.0.0.1 -p $(POSTGRES_HOST_PORT) -U $(POSTGRES_USER_VAL) -d koto -v ON_ERROR_STOP=1 -f services/user/migrations/004_banner.sql
	@PGPASSWORD=$(POSTGRES_PASSWORD_VAL) psql -h 127.0.0.1 -p $(POSTGRES_HOST_PORT) -U $(POSTGRES_USER_VAL) -d koto -v ON_ERROR_STOP=1 -f services/user/migrations/005_friend_requests.sql
	@PGPASSWORD=$(POSTGRES_PASSWORD_VAL) psql -h 127.0.0.1 -p $(POSTGRES_HOST_PORT) -U $(POSTGRES_USER_VAL) -d koto -v ON_ERROR_STOP=1 -f services/notification/migrations/001_devices.sql
	@PGPASSWORD=$(POSTGRES_PASSWORD_VAL) psql -h 127.0.0.1 -p $(POSTGRES_HOST_PORT) -U $(POSTGRES_USER_VAL) -d koto -v ON_ERROR_STOP=1 -f services/media/migrations/001_files.sql
	@PGPASSWORD=$(POSTGRES_PASSWORD_VAL) psql -h 127.0.0.1 -p $(POSTGRES_HOST_PORT) -U $(POSTGRES_USER_VAL) -d koto -v ON_ERROR_STOP=1 -f services/media/migrations/002_is_public.sql
	@PGPASSWORD=$(POSTGRES_PASSWORD_VAL) psql -h 127.0.0.1 -p $(POSTGRES_HOST_PORT) -U $(POSTGRES_USER_VAL) -d koto -v ON_ERROR_STOP=1 -f services/bot/migrations/001_bots.sql
	@echo "→ scylla (optional, needs cqlsh) …"
	@command -v cqlsh >/dev/null 2>&1 && cqlsh 127.0.0.1 9042 < services/chat/migrations/001_messages.cql || echo "  (skip: install cqlsh or run \`make migrate\` inside Docker)"
	@echo "✓ migrate-host complete"

# ─── Proto ────────────────────────────────────────────────────────────────────
## proto: generate Go code from .proto files (requires buf)
proto:
	@which buf > /dev/null || (echo "install buf: https://buf.build/docs/installation" && exit 1)
	buf generate pkg/proto

# ─── Quality ──────────────────────────────────────────────────────────────────
## lint: run golangci-lint across all services
lint:
	@which golangci-lint > /dev/null || (echo "install golangci-lint: https://golangci-lint.run/usage/install/" && exit 1)
	@for svc in $(SERVICES); do \
		echo "→ linting $$svc..."; \
		cd services/$$svc && golangci-lint run ./... && cd ../..; \
	done

## test: run tests for all services
test:
	@for svc in $(SERVICES); do \
		echo "→ testing $$svc..."; \
		cd services/$$svc && $(GO) test -race -count=1 ./... && cd ../..; \
	done

## tidy: tidy all go.mod files
tidy:
	@for svc in $(SERVICES); do \
		echo "→ tidying $$svc..."; \
		cd services/$$svc && $(GO) mod tidy && cd ../..; \
	done
	@for pkg in logger errors token; do \
		echo "→ tidying pkg/$$pkg..."; \
		cd pkg/$$pkg && $(GO) mod tidy && cd ../..; \
	done

## clean: remove build artifacts
clean:
	rm -rf bin/

## keygen: generate a new Ed25519 keypair for JWT
keygen:
	@go run infra/scripts/keygen/main.go

help:
	@grep -E '^## ' Makefile | sed 's/## //'

# ─── Production ──────────────────────────────────────────────────────────────
PROD_COMPOSE := docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.production

## prod-up: build & start production stack (Caddy на :80/:443, всё внутри сети)
prod-up:
	@test -f .env.production || (echo "Создайте .env.production по образцу .env.production.example"; exit 1)
	$(PROD_COMPOSE) up -d --build

## prod-down: stop production stack (volumes сохраняются)
prod-down:
	$(PROD_COMPOSE) down

## prod-logs: tail logs of all prod services (svc=<name> ограничит одним)
prod-logs:
	$(PROD_COMPOSE) logs -f --tail=200 $(svc)

## prod-migrate: применить миграции в prod БД
prod-migrate:
	$(PROD_COMPOSE) run --rm migrator || $(MAKE) migrate
