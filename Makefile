# ============================================================
# shipyard - Unified Build & Deploy Platform
# Makefile - Cross-platform (Linux/Mac/WSL/Git Bash)
# Windows users without make: see scripts/Makefile.ps1
# ============================================================

# Detect OS
ifeq ($(OS),Windows_NT)
    DETECTED_OS := Windows
    RM := rmdir /S /Q
else
    DETECTED_OS := $(shell uname -s)
    RM := rm -rf
endif

# Project metadata
PROJECT_NAME := shipyard
VERSION ?= 0.1.0
COMMIT := $(shell git rev-parse --short HEAD 2>/dev/null || echo "unknown")
BUILD_TIME := $(shell date -u +"%Y-%m-%dT%H:%M:%SZ")

# Docker
COMPOSE_FILE := docker-compose.yml
COMPOSE := docker compose
DOCKER_REGISTRY ?= ghcr.io/yourname

# ============================================================
# Default target
# ============================================================
.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help message
	@echo "shipyard - Unified Build & Deploy Platform"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Common targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'
	@echo ""
	@echo "Variables:"
	@echo "  VERSION=0.1.0     Set version (default: from git tag or 0.1.0)"
	@echo "  DOCKER_REGISTRY   Docker registry (default: ghcr.io/yourname)"

# ============================================================
# Setup
# ============================================================
.PHONY: setup
setup: setup-hooks ## Initial project setup (git hooks + config)
	@echo "[OK] Project setup complete"

.PHONY: setup-hooks
setup-hooks: ## Install git hooks (commitlint, pre-commit)
	@echo "Installing git hooks..."
	@git config core.hooksPath .githooks
	@chmod +x .githooks/commit-msg .githooks/pre-commit 2>/dev/null || true
	@echo "[OK] Git hooks installed"

# ============================================================
# Infrastructure (M1: MySQL + Redis only)
# ============================================================
.PHONY: infra
infra: ## Start MySQL + Redis only (lightweight, for development)
	@echo "Starting infrastructure (MySQL + Redis)..."
	@$(COMPOSE) up -d mysql redis
	@echo "[OK] MySQL on :3306, Redis on :6379"
	@echo "Run 'make shipyard-dev' or 'make web-dev' next"

.PHONY: infra-stop
infra-stop: ## Stop MySQL + Redis
	@$(COMPOSE) stop mysql redis

.PHONY: infra-logs
infra-logs: ## Tail MySQL + Redis logs
	@$(COMPOSE) logs -f mysql redis

# ============================================================
# Full demo (M15: shipyard + drone + Harbor + k3s + worker + monitoring)
# ============================================================
.PHONY: demo
demo: ## Start the full demo (shipyard + drone + Harbor + k3s + worker + monitoring)
	@echo "Starting full demo..."
	@echo "This will take ~3 minutes on first run (downloading images)..."
	@$(COMPOSE) -f docker-compose.demo.yml up -d
	@echo ""
	@echo "[OK] Demo is up!"
	@echo "  shipyard Web:   http://localhost:8080"
	@echo "  shipyard API:   http://localhost:8080/api"
	@echo "  drone CI:     http://localhost:8081"
	@echo "  Harbor:       http://localhost:8082"
	@echo "  Prometheus:   http://localhost:9090"
	@echo "  Grafana:      http://localhost:3000  (admin/admin)"

.PHONY: demo-stop
demo-stop: ## Stop the full demo
	@$(COMPOSE) -f docker-compose.demo.yml stop

.PHONY: demo-down
demo-down: ## Stop and remove the full demo (⚠ removes all data)
	@$(COMPOSE) -f docker-compose.demo.yml down -v

.PHONY: demo-logs
demo-logs: ## Tail all demo logs
	@$(COMPOSE) -f docker-compose.demo.yml logs -f

# ============================================================
# Development - shipyard backend (M2)
# ============================================================
.PHONY: shipyard-dev
shipyard-dev: ## Run shipyard backend (requires infra)
	@echo "Starting shipyard backend..."
	@cd shipyard && mvn spring-boot:run

.PHONY: shipyard-build
shipyard-build: ## Build shipyard backend jar
	@cd shipyard && mvn clean package -DskipTests

.PHONY: shipyard-test
shipyard-test: ## Run shipyard backend tests
	@cd shipyard && mvn test

.PHONY: shipyard-coverage
shipyard-coverage: ## Run shipyard backend tests with coverage
	@cd shipyard && mvn test jacoco:report
	@echo "[OK] Coverage report: shipyard/target/site/jacoco/index.html"

# ============================================================
# Development - shipyard Web (M3)
# ============================================================
.PHONY: web-dev
web-dev: ## Run shipyard Web UI (requires infra + shipyard-dev)
	@echo "Starting shipyard Web..."
	@cd web && pnpm install && pnpm dev

.PHONY: web-build
web-build: ## Build shipyard Web for production
	@cd web && pnpm install && pnpm build

.PHONY: web-test
web-test: ## Run shipyard Web tests
	@cd web && pnpm test

.PHONY: web-coverage
web-coverage: ## Run shipyard Web tests with coverage
	@cd web && pnpm test:coverage

# ============================================================
# V1 阶段 (V5 撤回后): 删 worker 目标, worker 走 in-process 模拟.
# V1.5+ 重新设计真接 worker 时再加回 worker-build / worker-test / worker-coverage.
# ============================================================

# ============================================================
# All tests + coverage (V1 阶段: 删 worker-test / worker-coverage)
# ============================================================
.PHONY: test
test: shipyard-test web-test ## Run all tests

.PHONY: coverage
coverage: shipyard-coverage web-coverage ## Generate all coverage reports

# ============================================================
# Code quality (V1 阶段: 删 worker lint/format)
# ============================================================
.PHONY: lint
lint: ## Run all linters
	@echo "Linting shipyard backend..."
	@cd shipyard && mvn spotless:check
	@echo "Linting web..."
	@cd web && pnpm lint
	@echo "[OK] Linting complete"

.PHONY: format
format: ## Auto-format all code
	@echo "Formatting shipyard backend..."
	@cd shipyard && mvn spotless:apply
	@echo "Formatting web..."
	@cd web && pnpm format
	@echo "[OK] Formatting complete"

# ============================================================
# Build Docker images
# ============================================================
.PHONY: docker-build
docker-build: docker-build-shipyard ## Build all Docker images (V1 阶段: 删 docker-build-worker)

.PHONY: docker-build-shipyard
docker-build-shipyard:
	@echo "Building shipyard image..."
	@cd shipyard && mvn clean package -DskipTests
	@docker build -t $(DOCKER_REGISTRY)/$(PROJECT_NAME)-shipyard:$(VERSION) -f Dockerfile.shipyard shipyard/
	@docker tag $(DOCKER_REGISTRY)/$(PROJECT_NAME)-shipyard:$(VERSION) $(DOCKER_REGISTRY)/$(PROJECT_NAME)-shipyard:latest

# V1 阶段 (V5 撤回后): 删 docker-build-worker, worker 走 shipyard in-process 模拟.
# V1.5+ 重新设计真接 worker 时再加回.

.PHONY: docker-push
docker-push: docker-build ## Build and push shipyard image to registry (V1 阶段: 删 worker push)
	@docker push $(DOCKER_REGISTRY)/$(PROJECT_NAME)-shipyard:$(VERSION)
	@docker push $(DOCKER_REGISTRY)/$(PROJECT_NAME)-shipyard:latest
	@echo "[OK] Shipyard image pushed to $(DOCKER_REGISTRY)"

# ============================================================
# Cleanup
# ============================================================
.PHONY: clean
clean: ## Remove build artifacts
	@cd shipyard && mvn clean 2>/dev/null
	@cd web && pnpm clean 2>/dev/null
	@cd worker && $(RM) bin coverage.out
	@echo "[OK] Cleaned"

.PHONY: clean-all
clean-all: clean demo-down ## Remove everything (artifacts + docker volumes)
	@echo "[OK] Everything cleaned"

# ============================================================
# Status
# ============================================================
.PHONY: status
status: ## Show status of all services
	@echo "=== Docker services ==="
	@$(COMPOSE) ps
	@echo ""
	@echo "=== Health check ==="
	@curl -s -o /dev/null -w "shipyard: %{http_code}\n" http://localhost:8080/actuator/health 2>/dev/null || echo "shipyard: not reachable"

.PHONY: version
version: ## Show version info
	@echo "$(PROJECT_NAME) v$(VERSION)"
	@echo "Commit:  $(COMMIT)"
	@echo "Built:   $(BUILD_TIME)"
	@echo "OS:      $(DETECTED_OS)"
