# master — 统一构建发布平台

<p align="center">
  <strong>统一构建发布平台</strong> · master-worker 架构 · 多环境 · 多仓库 · AI 增强
</p>

<p align="center">
  <a href="#快速开始"><img src="https://img.shields.io/badge/快速开始-make%20demo-blue?style=flat-square" alt="快速开始"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-green?style=flat-square" alt="License"></a>
  <a href="https://github.com/yourname/master/releases"><img src="https://img.shields.io/github/v/release/yourname/master?style=flat-square" alt="Release"></a>
  <a href=".github/workflows/ci.yml"><img src="https://img.shields.io/badge/CI-passing-brightgreen?style=flat-square" alt="CI"></a>
  <a href="#测试"><img src="https://img.shields.io/badge/coverage-70%25-blue?style=flat-square" alt="Coverage"></a>
  <a href="https://github.com/yourname/master/pkgs/container/master"><img src="https://img.shields.io/badge/docker-ghcr-blue?style=flat-square" alt="Docker"></a>
</p>

<p align="center">
  <a href="README.md">English</a> ·
  <a href="docs/architecture.md">架构文档</a> ·
  <a href="docs/superpowers/specs/2026-08-08-platform-design.md">设计 Spec</a> ·
  <a href="docs/interview-prep.md">面试讲稿</a>
</p>

---

> **一句话简介**：统一构建发布平台，master-worker 架构，支持 Java/Vue/React/Python 应用跨多环境发布，内置 AI 辅助（流水线生成、故障诊断、发布决策）。

---

## 📑 目录

- [🎯 为什么做 master？](#-为什么做-master)
- [✨ 核心特性](#-核心特性)
- [🏗️ 架构](#%EF%B8%8F-架构)
- [🚀 快速开始](#-快速开始)
- [📚 文档](#-文档)
- [🗺️ 路线图](#%EF%B8%8F-路线图)
- [📸 截图 & Demo](#-截图--demo)
- [🤝 贡献](#-贡献)
- [🛡️ 安全](#%EF%B8%8F-安全)
- [📄 许可](#-许可)

---

## 🎯 为什么做 master？

**痛点**：团队在多个项目 × 多个环境（dev/test/正式、多个地域）下，每周浪费大量时间在重复的构建/发布操作上。现有工具的不足：

- 锁死在单一云厂商 / 单一仓库平台
- 没有 AI 辅助写流水线、诊断失败、调优发布
- 改流水线要懂 drone YAML 语法,学习成本高

**解法**：master 是**一站式**发布平台:

- **一个 URL** 管理所有项目、环境、构建、发布
- **AI 辅助**三个最痛的点: 写流水线 / 诊断失败 / 评估发布风险
- **模板化 Dockerfile 生成**,项目不需要自己维护 Dockerfile
- **Master-worker 架构** + 每个环境独立 k8s 集群(物理隔离)
- **实时构建日志** SSE 推送(不用跳 drone 刷新)
- **Snapshot 回滚** 到任意历史版本(不只上一个)
- **开源** (Apache 2.0) + 可自部署

---

## ✨ 核心特性

### 🎛️ 平台核心
- **统一 UI** 管理项目、环境、构建、发布、告警、监控
- **Master-worker 架构** — master 是单一 API 门面,worker 部署在每个环境集群
- **多环境** (dev/test/正式,多地域) + 每个环境 worker 隔离
- **多仓库** — GitLab (V1) + Gitee (V1.5, 社区 PR 友好)
- **Drone CI 集成** + HMAC webhook 验签

### 🐳 构建 & 发布
- **可定制流水线** 存在 master,带版本控制和 AI 辅助编辑
- **模板化 Dockerfile 生成** — 选"Java/Maven"自动生成可用的 Dockerfile 推到仓库(走 MR)
- **环境变量加密** (AES-256,envelope encryption 设计方便升级 KMS)
- **Snapshot 部署** — 每次发布记完整 yaml 快照,回滚到任意版本
- **实时构建日志** SSE(不轮询,不刷新页面)

### 🤖 AI 能力
- **AI 流水线生成** — 描述需求,AI 草拟 `.drone.yml`
- **AI 故障诊断** — 构建失败时,AI 读日志给修复建议 + 置信度
- **AI 发布决策** — AI 分析变更范围给发布风险等级

### 🛡️ 可靠性
- **Prometheus 指标** master / worker / drone 全覆盖
- **结构化告警** (P0/P1/P2) + UI 展示
- **Worker 心跳** (2 分钟无响应自动标 unhealthy)
- **Property-based testing** 在关键 snapshot 拼装(jqwik)

### 🎬 Demo 友好
- **`make demo`** 一键起 master + MySQL + Redis + drone + Harbor + k3s + worker + Prometheus + Grafana
- **5 分钟 demo 视频** 展示 build → publish → rollback 全流程
- **8 个核心页面 wireframes** 文档在 `docs/superpowers/wireframes/`

---

## 🏗️ 架构

```
┌──────────────────────────────────────────────────────┐
│  开发者浏览器                                          │
│         ↓ HTTPS                                      │
│  master (Java 21 + Spring Boot 3.2+, 虚拟线程)        │
│   ├─ Web UI (Vue 3 + TS + Element Plus)              │
│   ├─ MySQL 8 (12 张表)                                │
│   ├─ Redis (缓存 + 分布式锁)                          │
│   ├─ LLM Adapter (通义/DeepSeek API, 默认 mock)       │
│   └─ Prometheus /actuator/prometheus                  │
│         ↓ drone CLI / REST                            │
│  drone CI                                            │
│         ↓ docker push                                │
│  Harbor                                              │
└──────────────────────────────────────────────────────┘
                                                      ↓
       ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
       │ dev k8s 集群      │  │ test k8s 集群    │  │ 正式 k8s 集群    │
       │   worker (Go)    │  │   worker (Go)    │  │   worker (Go)    │
       └──────────────────┘  └──────────────────┘  └──────────────────┘
                  ↓ 所有组件 /metrics
              Prometheus → Alertmanager
```

**完整架构**: [docs/architecture.md](docs/architecture.md)
**设计 Spec**: [docs/superpowers/specs/2026-08-08-platform-design.md](docs/superpowers/specs/2026-08-08-platform-design.md) (35KB, 8 张 Mermaid 图, 23 个设计决策)
**实现计划**: [docs/superpowers/plans/2026-08-08-platform-implementation.md](docs/superpowers/plans/2026-08-08-platform-implementation.md) (16 个 milestone, 5-6 周)

---

## 🚀 快速开始

> **前置**: Docker 24+, Docker Compose 2.x, Make, k3d (demo 集群)

```bash
# 1. 克隆
git clone https://github.com/yourname/master.git
cd master

# 2. (可选) 配置 LLM API key 启用真 AI;默认用 mock
export TONGYI_API_KEY="sk-xxxxx"

# 3. 一键起所有组件
make demo

# 4. 打开浏览器
open http://localhost:8080
```

demo 会起:
- master (端口 8080) — 主 UI + API
- MySQL (3306), Redis (6379)
- drone CI (8081) — 构建引擎
- Harbor (8082) — 镜像仓库
- k3s (6443) — 模拟 k8s 集群
- worker (in-cluster) — 部署代理
- Prometheus (9090), Grafana (3000)

**首次启动约 3 分钟** (下载镜像)

只起基础设施 (开发时用):
```bash
make infra          # 只起 MySQL + Redis
make master-dev     # 跑 master (IDE 或 mvn spring-boot:run)
make worker-dev     # 跑 worker (kubectl 指 k3s)
```

---

## 📚 文档

| 文档 | 用途 |
|---|---|
| [docs/architecture.md](docs/architecture.md) | 架构总览 |
| [docs/superpowers/specs/2026-08-08-platform-design.md](docs/superpowers/specs/2026-08-08-platform-design.md) | **完整设计 spec** (23 决策, 8 Mermaid 图) |
| [docs/superpowers/specs/2026-08-08-platform-design.html](docs/superpowers/specs/2026-08-08-platform-design.html) | Spec 浏览器版 (含 Mermaid 渲染) |
| [docs/superpowers/plans/2026-08-08-platform-implementation.md](docs/superpowers/plans/2026-08-08-platform-implementation.md) | 实现计划 (16 milestone, 5-6 周) |
| [docs/superpowers/wireframes/index.html](docs/superpowers/wireframes/index.html) | 8 个核心页面 wireframes (浏览器开) |
| [docs/demo/](docs/demo/) | 5 分钟 demo 视频 + 截图 |
| [docs/interview-prep.md](docs/interview-prep.md) | 5 分钟讲稿 + 20 Q&A + 3 踩坑故事 (V1 release 包含) |

---

## 🗺️ 路线图

### V1 (当前, 5-6 周) — 横向 demo
- [x] Master-worker 架构
- [x] GitLab + Gitee 仓库抽象 (GitLab V1, Gitee stub)
- [x] Drone CI 集成 + HMAC webhook
- [x] 可定制流水线 + 版本控制
- [x] AI 流水线生成 (默认 mock, 真 LLM 配 API key)
- [x] 实时构建日志 (SSE)
- [x] Snapshot 部署 + 回滚
- [x] AI 故障诊断
- [x] AI 发布决策
- [x] 模板化 Dockerfile 生成 (4-5 套主流模板)
- [x] Prometheus 指标 + 告警日志
- [x] Property-based testing (snapshot 拼装)
- [x] `make demo` 一键起

### V1.5 (V1 后, 3-4 周)
- [ ] 多环境(独立 k8s 集群)dev/test/正式
- [ ] Gitee 适配器 (社区 PR 友好)
- [ ] 飞书/钉钉 webhook 告警
- [ ] 流水线 webhook 自动触发
- [ ] Vue/React/Python 端到端 E2E 测试
- [ ] 手动停止部署按钮
- [ ] 用户自维护 Dockerfile 模板

### V2 (长期)
- [ ] 完整 RBAC 权限
- [ ] 审批流 (多级 + 灰度)
- [ ] 蓝绿/金丝雀发布 (Argo Rollouts)
- [ ] 私有部署 LLM (Ollama + Qwen)
- [ ] 完整 Grafana dashboard + Alertmanager 升级

---

## 📸 截图 & Demo

> v0.1.0 release 时发布 (见 [docs/demo/](docs/demo/))

8 个核心页面 wireframes: 在浏览器打开 [docs/superpowers/wireframes/index.html](docs/superpowers/wireframes/index.html)

---

## 🤝 贡献

欢迎贡献! 见 [CONTRIBUTING.md](CONTRIBUTING.md):
- 开发环境搭建
- 怎么提 PR
- 代码规范
- 测试要求
- 提交信息规范 (Conventional Commits)

**好的 first issue** (新贡献者友好):
- [ ] 实现 `GiteeAdapter` (interface 已在 `master/src/main/java/com/master/repo/` 定义)
- [ ] 加更多 Dockerfile 模板 (Python/Flask, Go/Gin, Vue/Vite)
- [ ] 加 Vue/React/Python E2E 测试

**行为准则**: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)

---

## 🛡️ 安全

发现漏洞? 请私下报告 — 见 [SECURITY.md](SECURITY.md) 了解披露政策和联系方式。

---

## 📄 许可

Copyright 2026 The master Platform Authors

Licensed under the Apache License, Version 2.0. 见 [LICENSE](LICENSE) 完整文本。

可在 <http://www.apache.org/licenses/LICENSE-2.0> 获取 License 副本。

---

## 🌟 Star History

如果 master 对你有帮助,考虑在 GitHub 上点个 star — 帮助其他人发现这个项目。

---

<p align="center">
  为每天交付的 DevOps 团队用 ❤️ 构建
</p>
