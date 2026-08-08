# AGENTS.md

Unified build & deploy platform with shipyard-worker architecture, supporting Java/Vue/React/Python across multiple environments, with built-in AI assistance (pipeline generation, failure diagnosis, release decisions).

## Setup commands

- Install deps: `make setup` (one-time, installs git hooks)
- Start infra: `make infra` (MySQL :3306 + Redis :6379)
- Start full demo: `make demo` (M15 only; pulls shipyard + drone + Harbor + k3s + worker + monitoring, ~3 min cold start)
- Run backend: `make shipyard-dev` (requires infra; Spring Boot on :8080)
- Run web: `make web-dev` (requires infra + backend; Vite dev server)
- Run worker: `make worker-dev`
- Build all: `make docker-build`
- Lint all: `make lint` (mvn spotless / go vet + golangci-lint / pnpm lint)
- Format all: `make format`
- Test all: `make test`
- Coverage: `make coverage` (shipyard JaCoCo / worker go test -cover / web pnpm test:coverage)

## Project layout

Multi-stack monorepo with three independent sub-projects sharing a single Makefile:

- `shipyard/` — Spring Boot 3.2 + Java 21 + Maven backend (the platform API)
  - `shipyard/src/main/java/com/shipyard/` — package root (config, crypto, variable, build, deploy, …)
  - `shipyard/src/main/resources/db/migration/` — Flyway migrations (V1__init.sql has 11 tables)
  - `shipyard/src/test/java/com/shipyard/crypto/` — crypto roundtrip tests
- `web/` — Vue 3 + TypeScript + Vite + pnpm frontend
- `worker/` — Go deploy worker (per-environment, talks to k8s)
- `docs/` — long-form documentation
  - `docs/architecture.md` — architecture stub, links to spec
  - `docs/superpowers/specs/2026-08-08-platform-design.md` — V1 design spec (23 decisions, 8 Mermaid diagrams)
  - `docs/superpowers/plans/2026-08-08-platform-implementation.md` — 16-milestone implementation plan
  - `docs/superpowers/wireframes/` — 8 low-fi page wireframes
- `scripts/` — repo utility scripts (build_spec_html, rename_to_shipyard)
- `.github/` — issue templates, PR template, dependabot, CODEOWNERS

## Code style

- **Java (shipyard)**: Spotless enforced via `mvn spotless:check` / `mvn spotless:apply`. Java 21 LTS features allowed (virtual threads, records, sealed classes).
- **Vue 3 + TS (web)**: ESLint + Prettier, `pnpm lint` / `pnpm format`. Strict TypeScript.
- **Go (worker)**: `gofmt` + `go vet` + `golangci-lint`. CGO disabled in production builds (`CGO_ENABLED=0`).
- Single source of truth: the Makefile target for each language is the canonical way to run the tool.
- Run `make format` before committing; CI-style checks via `make lint`.

## Testing instructions

- All three sub-projects have their own test runner — `make test` runs them all in sequence.
- **Coverage threshold: 70%** (see CONTRIBUTING.md "Coverage Thresholds"). Coverage reports land in `shipyard/target/site/jacoco/`, `worker/coverage.html`, `web/coverage/`.
- **Unit tests**: backend JUnit 5 + Mockito; worker stdlib `testing`; web Vitest.
- Add tests for every new behavior in the same package, mirroring existing test file naming.
- Property-based tests required for crypto / serialisation / parsers (see CONTRIBUTING.md "Where Property-Based Tests Are Required").
- All tests must pass before opening a PR.

## Domain rules (must read before changing code)

- **V1 scope is "horizontal demo first"**: one Java app end-to-end (build → push → deploy → rollback + AI three capabilities). Don't expand scope; that's V1.5.
- **Project naming is `shipyard` (not `master`)** — service name, branch prefix, repo name all aligned. Past `master` references are gone (rename commit `fb0b57b`).
- **Default LLM is MockLLMAdapter** (returns canned data). Real LLM (`TONGYI_API_KEY` or `DEEPSEEK_API_KEY`) is opt-in via env var. All three AI capabilities share one adapter.
- **Encrypted env vars** use AES-256 with envelope encryption designed for KMS upgrade. Key file `shipyard.key` is git-ignored — never commit it.
- **Real-time build logs** flow via SSE, persisted to shipyard MySQL by step. shipyard keeps them forever; drone keeps 30 days.
- **Health check**: `GET /actuator/health` on the backend (port 8080). Use this to verify the app is up.
- **Single backend API root**: `http://localhost:8080/api`.
- **Migrations are append-only**: never edit a `V*__*.sql` that has been applied — add a new `V<n+1>__*.sql`.
- **Dockerfile templates are shipyard-owned**: 4–5 stock templates (Java/Maven, Java/Gradle, Node/pnpm, Python/poetry) live in shipyard and get committed into the target project repo via MR.
- **Open source (Apache 2.0)**: no internal credentials, URLs, or domain names in code or commits.
- **Interview project**: choices are made to be explained in a 5-min walkthrough with measurable outcomes (N modules, X E2E tests, Y% coverage). Architectural clarity > feature breadth.
- **PR & commit conventions** (Conventional Commits enforced by `commitlint` hook): branch from `main`, never push to `main` directly, one logical change per commit, message body lists concrete items (not abstract phase labels). See `CONTRIBUTING.md` for the full list of types and examples.
- **Roadmap of remaining milestones**: see `docs/superpowers/plans/2026-08-08-platform-implementation.md` (M2 backend skeleton → M15 full demo). `PROGRESS.md` tracks current milestone and per-machine handoff.
- **No CI workflow exists yet** — `.github/workflows/` is empty by design (M15). Don't add GitHub Actions until then; `make demo` is the canonical end-to-end verification.

## Security

- Never commit secrets — `.env`, `*.pem`, `*.key`, `*.crt`, `secrets/`, `credentials/`, `shipyard.key` are all in `.gitignore`.
- Use `${XXX_API_KEY:}` placeholders in `application.yml`; pass real values via env vars.
- `SECURITY.md` has the 90-day vulnerability disclosure SLA.
- `CODEOWNERS` defines per-directory review ownership — respect it on PRs.
- API tokens / LLM keys must never appear in chat or commit messages; even if "immediately reset", the chat log is already persisted.

## PR & commit conventions

- Branch from `main`; never push to `main` directly.
- Commit message: Conventional Commits (`feat:` / `fix:` / `docs:` / `refactor:` / `test:` / `chore:` / `perf:`).
- One logical change per commit; commit body must list concrete changes (files + what changed), not abstract phase labels.
- Open PR via `gh pr create` once local `make test` + `make lint` are green.
- PR template (`.github/PULL_REQUEST_TEMPLATE.md`) has the full checklist — fill it in.
