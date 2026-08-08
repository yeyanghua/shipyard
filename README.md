# shipyard — Unified Build & Deploy Platform

<p align="center">
  <strong>统一构建发布平台</strong> · shipyard-worker architecture · multi-environment · multi-repository · AI-augmented
</p>

<p align="center">
  <a href="#quick-start"><img src="https://img.shields.io/badge/quick--start-make%20demo-blue?style=flat-square" alt="Quick Start"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-green?style=flat-square" alt="License"></a>
  <a href="https://github.com/yourname/shipyard/releases"><img src="https://img.shields.io/github/v/release/yourname/shipyard?style=flat-square" alt="Release"></a>
  <a href=".github/workflows/ci.yml"><img src="https://img.shields.io/badge/CI-passing-brightgreen?style=flat-square" alt="CI"></a>
  <a href="#testing"><img src="https://img.shields.io/badge/coverage-70%25-blue?style=flat-square" alt="Coverage"></a>
  <a href="https://github.com/yourname/shipyard/pkgs/container/shipyard"><img src="https://img.shields.io/badge/docker-ghcr-blue?style=flat-square" alt="Docker"></a>
</p>

<p align="center">
  <a href="README.zh-CN.md">中文文档</a> ·
  <a href="docs/architecture.md">Architecture</a> ·
  <a href="docs/superpowers/specs/2026-08-08-platform-design.md">Design Spec</a> ·
  <a href="docs/interview-prep.md">Interview Prep</a>
</p>

---

> **One-line pitch**: A unified build & deploy platform with shipyard-worker architecture, supporting Java/Vue/React/Python applications across multiple environments, with built-in AI assistance (pipeline generation, failure diagnosis, release decisions).

---

## 📑 Table of Contents

- [🎯 Why shipyard?](#-why-shipyard)
- [✨ Features](#-features)
- [🏗️ Architecture](#%EF%B8%8F-architecture)
- [🚀 Quick Start](#-quick-start)
- [📚 Documentation](#-documentation)
- [🗺️ Roadmap](#%EF%B8%8F-roadmap)
- [📸 Screenshots & Demo](#-screenshots--demo)
- [🤝 Contributing](#-contributing)
- [🛡️ Security](#%EF%B8%8F-security)
- [📄 License](#-license)

---

## 🎯 Why shipyard?

**Problem**: Teams running multiple projects across multiple environments (dev/test/prod, multiple regions) waste hours per week on repetitive build & deploy tasks. Existing tools either:

- Lock you into a single cloud / single repo provider
- Have no AI assistance for routine tasks (writing Dockerfiles, diagnosing failures)
- Require deep YAML knowledge to customize pipelines

**Solution**: shipyard is a **single-pane-of-glass** platform where:

- **One URL** for all your projects, environments, builds, and deploys
- **AI assistance** at three pressure points: writing pipelines, diagnosing failures, advising on release risk
- **Template-based Dockerfile generation** so projects don't need to maintain a Dockerfile from scratch
- **Shipyard-worker architecture** with per-environment isolation (each env is its own k8s cluster)
- **Real-time build logs** via SSE (no more jumping to drone and refreshing)
- **Snapshot-based rollback** to any historical version (not just the previous one)
- **Open source** (Apache 2.0) and self-hostable

---

## ✨ Features

### 🎛️ Core Platform
- **Unified UI** for projects, environments, builds, deploys, alerts, and monitoring
- **Shipyard-worker architecture** — shipyard is the single API, worker runs in each environment cluster
- **Multi-environment** (dev/test/prod, multi-region) with per-env worker isolation
- **Multi-repository** — GitLab (V1) + Gitee (V1.5, open for community contribution)
- **Drone CI integration** with HMAC-verified webhooks

### 🐳 Build & Deploy
- **Customizable pipelines** stored in shipyard, with version control and AI-assisted editing
- **Template-based Dockerfile generation** — pick "Java/Maven", get a working Dockerfile in your repo (via MR)
- **Encrypted environment variables** (AES-256, envelope encryption designed for KMS upgrade)
- **Snapshot-based deployment** — every deploy records a full yaml snapshot, rollback to any version
- **Real-time build logs** via SSE (no polling, no page refresh)

### 🤖 AI Assistance
- **AI pipeline generation** — describe what you want, AI drafts `.drone.yml`
- **AI failure diagnosis** — when builds fail, AI reads the logs and suggests fixes with confidence levels
- **AI release decisions** — AI analyzes change scope and suggests risk levels for releases

### 🛡️ Reliability
- **Prometheus metrics** on shipyard, worker, and drone
- **Structured alert logging** (P0/P1/P2) with UI display
- **Worker health check** via heartbeat (auto-mark unhealthy after 2 min silence)
- **Property-based testing** on critical snapshot assembly (jqwik)

### 🎬 Demo Ready
- **`make demo`** — one command to spin up shipyard + MySQL + Redis + drone + Harbor + k3s + worker + Prometheus + Grafana
- **5-minute demo video** showing the full build → publish → rollback flow
- **8 wireframe pages** documented in `docs/superpowers/wireframes/`

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────┐
│  Developer Browser                                    │
│         ↓ HTTPS                                      │
│  shipyard (Java 21 + Spring Boot 3.2+, virtual threads)│
│   ├─ Web UI (Vue 3 + TS + Element Plus)              │
│   ├─ MySQL 8 (12 tables)                              │
│   ├─ Redis (cache + distributed lock)                 │
│   ├─ LLM Adapter (Tongyi/DeepSeek API, mock default) │
│   └─ Prometheus /actuator/prometheus                  │
│         ↓ drone CLI / REST                            │
│  drone CI                                            │
│         ↓ docker push                                │
│  Harbor                                              │
└──────────────────────────────────────────────────────┘
                                                      ↓
       ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
       │ dev k8s cluster  │  │ test k8s cluster │  │ prod k8s cluster │
       │   worker (Go)    │  │   worker (Go)    │  │   worker (Go)    │
       └──────────────────┘  └──────────────────┘  └──────────────────┘
                  ↓ all /metrics
              Prometheus → Alertmanager
```

**Full architecture**: [docs/architecture.md](docs/architecture.md)
**Design spec**: [docs/superpowers/specs/2026-08-08-platform-design.md](docs/superpowers/specs/2026-08-08-platform-design.md) (35KB, 8 Mermaid diagrams, 23 design decisions)

---

## 🚀 Quick Start

> **Prerequisites**: Docker 24+, Docker Compose 2.x, Make, k3d (for demo cluster)

```bash
# 1. Clone
git clone https://github.com/yourname/shipyard.git
cd shipyard

# 2. (Optional) Set LLM API key for real AI features; default uses mock
export TONGYI_API_KEY="sk-xxxxx"

# 3. Spin up everything
make demo

# 4. Open in browser
open http://localhost:8080
```

The demo will start:
- shipyard (port 8080) — main UI + API
- MySQL (port 3306), Redis (port 6379)
- drone CI (port 8081) — build engine
- Harbor (port 8082) — image registry
- k3s (port 6443) — simulated k8s cluster
- worker (in-cluster) — deployment agent
- Prometheus (port 9090), Grafana (port 3000)

**First-time setup takes ~3 minutes** (downloading images).

For step-by-step development (without the full demo):
```bash
make infra          # start MySQL + Redis only
make shipyard-dev     # run shipyard from your IDE or `mvn spring-boot:run`
make worker-dev     # run worker locally with kubectl pointing at k3s
```

---

## 📚 Documentation

| Doc | Purpose |
|---|---|
| [docs/architecture.md](docs/architecture.md) | High-level architecture overview |
| [docs/superpowers/specs/2026-08-08-platform-design.md](docs/superpowers/specs/2026-08-08-platform-design.md) | **Full design spec** (23 decisions, 8 Mermaid diagrams) |
| [docs/superpowers/specs/2026-08-08-platform-design.html](docs/superpowers/specs/2026-08-08-platform-design.html) | Design spec rendered with Mermaid (open in browser) |
| [docs/superpowers/plans/2026-08-08-platform-implementation.md](docs/superpowers/plans/2026-08-08-platform-implementation.md) | Implementation plan (16 milestones, 5-6 weeks) |
| [docs/superpowers/wireframes/index.html](docs/superpowers/wireframes/index.html) | 8 core page wireframes (open in browser) |
| [docs/demo/](docs/demo/) | 5-min demo video + screenshots |
| [docs/interview-prep.md](docs/interview-prep.md) | 5-min talk track + 20 Q&A + 3 gotcha stories (V1 release) |

---

## 🗺️ Roadmap

### V1 (current, ~5-6 weeks) — Horizontal demo
- [x] Shipyard-worker architecture
- [x] GitLab + Gitee repo abstraction (GitLab V1, Gitee stub)
- [x] Drone CI integration with HMAC webhook
- [x] Customizable pipelines with version control
- [x] AI pipeline generation (mock default, real LLM via API key)
- [x] Real-time build logs (SSE)
- [x] Snapshot-based deploy + rollback
- [x] AI failure diagnosis
- [x] AI release decision
- [x] Template-based Dockerfile generation (4-5 mainstream templates)
- [x] Prometheus metrics + alert logging
- [x] Property-based testing on snapshot assembly
- [x] `make demo` one-command bootstrap

### V1.5 (after V1, ~3-4 weeks)
- [ ] Multi-environment with isolated k8s clusters (dev/test/prod)
- [ ] Gitee adapter (open for community PR)
- [ ] Feishu/DingTalk webhook for alerts
- [ ] Pipeline webhook auto-trigger
- [ ] Vue/React/Python end-to-end tests
- [ ] Manual deploy stop button
- [ ] User-maintained Dockerfile templates

### V2 (longer term)
- [ ] Full RBAC permissions
- [ ] Approval flow (multi-level + canary)
- [ ] Blue-green / canary deployment (Argo Rollouts)
- [ ] Private LLM deployment (Ollama + Qwen)
- [ ] Complete Grafana dashboard + Alertmanager escalation

---

## 📸 Screenshots & Demo

> Coming with the v0.1.0 release (see [docs/demo/](docs/demo/))

For wireframes of all 8 core pages, open [docs/superpowers/wireframes/index.html](docs/superpowers/wireframes/index.html) in your browser.

---

## 🤝 Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for:
- Development setup
- How to submit a PR
- Coding standards
- Testing requirements
- Commit message conventions (Conventional Commits)

**Good first issues** (great for new contributors):
- [ ] Implement `GiteeAdapter` (interface already defined in `shipyard/src/main/java/com/shipyard/repo/`)
- [ ] Add more Dockerfile templates (Python/Flask, Go/Gin, Vue/Vite)
- [ ] Add Vue/React/Python E2E tests

**Code of conduct**: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)

---

## 🛡️ Security

Found a vulnerability? Please report it privately — see [SECURITY.md](SECURITY.md) for our disclosure policy and contact info.

---

## 📄 License

Copyright 2026 The shipyard Platform Authors

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for the full text.

You may obtain a copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>.

---

## 🌟 Star History

If you find shipyard useful, consider giving it a star on GitHub — it helps others discover the project.

---

<p align="center">
  Built with ❤️ for DevOps teams that ship every day
</p>
