# Contributing to master

First off, thank you for considering contributing to master! 🎉

This document is a guide to help you contribute effectively. Following these guidelines helps communicate that you respect the time of the developers managing and developing this open source project.

## 📑 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Testing Requirements](#testing-requirements)
- [Commit Message Convention](#commit-message-convention)
- [Issue Reporting](#issue-reporting)

---

## Code of Conduct

This project and everyone participating in it is governed by our [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

---

## How Can I Contribute?

### 🐛 Reporting Bugs

Before creating bug reports, please check the issue list to see if the problem has already been reported. When you create a bug report, please include as many details as possible:

- **Use a clear and descriptive title**
- **Describe the exact steps to reproduce the problem**
- **Provide specific examples** (code snippets, screenshots, logs)
- **Describe the behavior you observed** and what you expected
- **Include details about your environment** (OS, Java version, Docker version, etc.)

Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md).

### 💡 Suggesting Features

Feature requests are welcome! Please use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.md) and provide:

- **The problem you're trying to solve**
- **Your proposed solution**
- **Alternatives you've considered**
- **Why this would be useful to most users**

### 🔧 Submitting Code Changes

1. **Pick an issue** or create one to discuss your idea first
2. **Fork the repo** and create a feature branch
3. **Make your changes** following our coding standards
4. **Write tests** covering your changes
5. **Run the test suite** locally before pushing
6. **Submit a Pull Request** using the [PR template](.github/PULL_REQUEST_TEMPLATE.md)

### 📝 Improving Documentation

Documentation improvements are always welcome! Whether it's fixing typos, adding examples, or clarifying confusing sections, every contribution helps.

### 🌟 Good First Issues

New to the project? Look for issues labeled [`good first issue`](../../issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22). Some classics:

- **Implement `GiteeAdapter`** — interface is already defined in `master/src/main/java/com/master/repo/`
- **Add more Dockerfile templates** — Python/Flask, Go/Gin, Vue/Vite
- **Add Vue/React/Python E2E tests**
- **Translate documentation** to other languages

---

## Development Setup

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| **JDK** | 21 LTS | Required for master (virtual threads) |
| **Go** | 1.22+ | For worker |
| **Node.js** | 20 LTS | For Web UI |
| **Maven** | 3.9+ | Java dependency management |
| **pnpm** | 9.x | JavaScript package manager (preferred over npm/yarn) |
| **Docker** | 24+ | For running infrastructure locally |
| **Docker Compose** | 2.x | For `make infra` and `make demo` |
| **Make** | any | For running `make` commands |
| **k3d** | latest | For `make demo` (simulated k8s) |

### Quick Start

```bash
# 1. Fork and clone
git clone https://github.com/yourname/master.git
cd master

# 2. Start infrastructure (MySQL + Redis)
make infra

# 3. Run master backend (in another terminal)
make master-dev
# or: cd master && mvn spring-boot:run

# 4. Run master Web (in another terminal)
make web-dev
# or: cd web && pnpm install && pnpm dev

# 5. (Optional) Run worker locally
make worker-dev
```

The application will be at:
- master Web: http://localhost:5173
- master API: http://localhost:8080
- MySQL: localhost:3306 (user: master, pass: master)
- Redis: localhost:6379

### Running the Full Demo

If you want to spin up everything (master + drone + Harbor + k3s + worker + monitoring):

```bash
make demo
# Then open http://localhost:8080
```

This takes ~3 minutes on first run (downloading images).

---

## Pull Request Process

1. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```

2. **Make atomic commits** — one logical change per commit

3. **Write good commit messages** (see [Commit Message Convention](#commit-message-convention))

4. **Push to your fork**:
   ```bash
   git push origin feat/your-feature-name
   ```

5. **Open a Pull Request** against `main`:
   - Fill out the [PR template](.github/PULL_REQUEST_TEMPLATE.md) completely
   - Link related issues (e.g., "Closes #123")
   - Add screenshots/screen recordings for UI changes
   - Ensure CI passes (tests + lint + coverage)

6. **Wait for review** — at least one approval is required before merge

7. **Address review feedback** by pushing additional commits (don't force-push during review)

8. **Squash and merge** — maintainers will squash your commits when merging to keep history clean

### PR Checklist

Before marking your PR as ready for review, ensure:

- [ ] All tests pass (`make test`)
- [ ] Code is formatted (`make format`)
- [ ] Linter passes (`make lint`)
- [ ] Coverage meets the threshold (master ≥ 70%, worker ≥ 60%, web ≥ 50%)
- [ ] New code has tests
- [ ] New public APIs are documented
- [ ] Commits follow Conventional Commits format
- [ ] CHANGELOG.md is updated (for user-facing changes)
- [ ] Spec / wireframes / docs are updated (for behavior changes)

---

## Coding Standards

### Java (master backend)

- **Style**: Google Java Style (enforced by spotless)
- **Java version**: 21 LTS (we use virtual threads!)
- **Framework**: Spring Boot 3.2+
- **Testing**: JUnit 5 + Mockito + Testcontainers + jqwik (for property-based tests)
- **Linting**: spotless + checkstyle

Key conventions:
- Use **constructor injection** instead of `@Autowired` field injection
- **Don't** use `synchronized` for I/O (use `ReentrantLock` instead — see [Spec §3.2.1](docs/superpowers/specs/2026-08-08-platform-design.md#321-虚拟线程设计java-21))
- **Don't** pool virtual threads (use `Executors.newVirtualThreadPerTaskExecutor()`)
- All public APIs have Javadoc
- Methods > 30 lines should be refactored

### Vue 3 + TypeScript (master Web)

- **Style**: ESLint + Prettier
- **Vue version**: 3.4+
- **Component library**: Element Plus
- **State management**: Pinia
- **Testing**: Vitest + Vue Test Utils

Key conventions:
- Use `<script setup lang="ts">` syntax
- Component names in PascalCase
- Props/emits typed with TypeScript interfaces
- Use `ref` for primitives, `reactive` for objects (or just `ref` always)

### Go (worker)

- **Style**: `gofmt` + `go vet` + `golangci-lint`
- **Go version**: 1.22+
- **Framework**: client-go for k8s integration
- **Testing**: standard `testing` + testify

Key conventions:
- Use `errors.Wrap` from `pkg/errors` for error context
- Public functions have Go doc comments
- No global state (use dependency injection)

---

## Testing Requirements

### Coverage Thresholds

| Component | Minimum line coverage |
|---|---|
| master backend | 70% |
| worker (Go) | 60% |
| master Web (Vue) | 50% |

Coverage is checked in CI; PRs that drop coverage below the threshold will be blocked.

### Test Categories

- **Unit tests** — test individual functions/classes in isolation
- **Integration tests** — use Testcontainers to spin up real MySQL/Redis/Drone/Harbor in Docker
- **Property-based tests** — use jqwik (Java) / gopter (Go) for input domain testing
- **Component tests** — Vue Test Utils for individual components
- **E2E tests** — Playwright for full flow (run during demo, not on every PR)

### Where Property-Based Tests Are Required

- **Snapshot YAML assembly** (`SnapshotBuilder.java`) — special characters in envvars, unicode, oversized values, etc.

---

## Commit Message Convention

We use [Conventional Commits](https://www.conventionalcommits.org/). This enables automatic CHANGELOG generation and semantic versioning.

### Format

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types

- **feat**: New user-facing feature
- **fix**: Bug fix
- **docs**: Documentation only changes
- **style**: Code style changes (formatting, no logic change)
- **refactor**: Code refactor (no behavior change)
- **perf**: Performance improvement
- **test**: Adding or fixing tests
- **build**: Build system / dependency changes
- **ci**: CI configuration changes
- **chore**: Other changes that don't modify src or test files

### Examples

```bash
feat(build): add Dockerfile template generation
fix(deploy): rollback fails when snapshot_yaml is empty
docs(spec): clarify k8s start failure handling
test(master): add PBT for snapshot builder
ci: enable Dependabot for Maven dependencies
```

### Commit Linting

We use `commitlint` to enforce this format. Install the pre-commit hook:

```bash
make setup-hooks
```

---

## Issue Reporting

### Search Before You Create

Please search the existing issues to see if your problem has already been reported. This avoids duplicate issues.

### Use Templates

We provide templates for:
- [Bug reports](.github/ISSUE_TEMPLATE/bug_report.md)
- [Feature requests](.github/ISSUE_TEMPLATE/feature_request.md)

Using them helps us understand and reproduce your issue faster.

### Be Specific

- **Title**: Short, descriptive, search-friendly
- **Description**: What you tried, what you expected, what happened
- **Environment**: OS, version, configuration
- **Logs/Screenshots**: If applicable

---

## Questions?

- 💬 Open a [GitHub Discussion](../../discussions)
- 📧 Email: maintainers@master-platform.dev (placeholder, real email in v0.1.0 release)

---

## License

By contributing to master, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
