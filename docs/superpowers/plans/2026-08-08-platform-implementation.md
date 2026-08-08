# 统一构建发布平台 — 实现计划

| 字段 | 值 |
|---|---|
| 版本 | 0.1.0 |
| 创建日期 | 2026-08-08 |
| 对应 spec | `2026-08-08-platform-design.md` (v0.5, 23 个决策) |
| V1 总工时 | **5-6 周**(3-4 周主体 + 1-2 周 demo 编排) |
| 总 milestone | 16 个 |
| 总任务 | 80+ 个 |

---

## 1. 总体里程碑图

```
Week 1 (基础设施 + 后端骨架)         Week 2 (构建链路)            Week 3 (发布链路 + AI)
├─ M1 仓库骨架                       ├─ M5 drone 集成              ├─ M8 worker Go
├─ M2 master 后端骨架                ├─ M6 pipeline 模板管理       ├─ M9 snapshot + 回滚
├─ M3 master Web 骨架                └─ M7 env_variable + 注入     ├─ M10 LLM adapter
└─ M4 repo 抽象层 + GitLab                                          └─ M11 监控告警

Week 4 (补全能力)                   Week 5-6 (demo + release)
├─ M12 Dockerfile 模板               ├─ M15 demo 编排
├─ M13 实时日志 SSE                   │   ├─ docker-compose + k3s
└─ M14 CI/测试/质量门                │   ├─ 录 demo 视频
                                     │   ├─ 写 README + 架构图
                                     │   └─ 准备 interview-prep
                                     └─ M16 Release v0.1.0
```

---

## 2. Milestone 详表

> **格式约定**：
> - **目标**：这个 milestone 结束时的可验收状态
> - **涉及文件**：粗粒度文件/目录
> - **验收标准**：怎么算"做完了"
> - **测试**：怎么测
> - **估算**：工时
> - **依赖**：必须先完成的前置 milestone

---

### M1 - 仓库骨架

| 项 | 内容 |
|---|---|
| **目标** | 仓库能 clone 后 `make demo` 跑通（哪怕只起 MySQL/Redis） |
| **涉及文件** | `README.md`, `README.zh-CN.md`, `LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `.gitignore`, `Makefile`, `docker-compose.yml`(基础设施部分), `docs/architecture.md`(stub) |
| **验收** | • GitHub 仓库创建(从 Gitee 同步或新建)<br>• 7 个社区基础设施文件齐备<br>• docker-compose 起 MySQL + Redis 成功<br>• Makefile 至少含 `make demo`, `make test`, `make lint` |
| **测试** | 验收：clone 后 `docker-compose up -d mysql redis` 起得来 |
| **估算** | 0.5 天 |
| **依赖** | 无 |

---

### M2 - master 后端骨架

| 项 | 内容 |
|---|---|
| **目标** | Spring Boot 3 应用能起，11 张表 schema 落库，基础 CRUD 可用 |
| **涉及文件** | `master/pom.xml`, `master/src/main/java/.../Application.java`, `master/src/main/resources/application.yml`, `master/src/main/resources/db/migration/V1__init.sql` |
| **验收** | • Java 21 + Spring Boot 3.2+<br>• 虚拟线程配置生效(`spring.threads.virtual.enabled=true`)<br>• 11 张表用 Flyway 迁移落库<br>• `/actuator/health` 返回 UP<br>• 基础 JWT 鉴权(白名单模式)<br>• `/api/projects` CRUD 至少 `GET /api/projects` 工作 |
| **测试** | • 单元测试: Encrypter 加解密(必须)<br>• 集成测试: Testcontainers MySQL 真实容器跑通<br>• 验证虚拟线程:JFR 或 thread dump 看是不是虚拟线程 |
| **估算** | 2-3 天 |
| **依赖** | M1 |

---

### M3 - master Web 骨架

| 项 | 内容 |
|---|---|
| **目标** | Vue 3 + TS + Element Plus + Pinia + Vue Router 基础应用跑通，能登录进 Dashboard |
| **涉及文件** | `web/package.json`, `web/vite.config.ts`, `web/src/main.ts`, `web/src/router/index.ts`, `web/src/stores/`, `web/src/views/Dashboard.vue` + 7 个其他页面 stub |
| **验收** | • `npm run dev` 起来<br>• 路由表包含 spec §4.2 全部 endpoint 对应页面<br>• 8 个页面有 stub 组件(占位即可)<br>• 顶部 + 侧边栏布局稳定<br>• 主题色对齐 Element Plus 默认 |
| **测试** | • Vitest 基础 setup<br>• Vue Test Utils 跑通一个 stub 组件 |
| **估算** | 1-2 天 |
| **依赖** | M2(API 能调) |

---

### M4 - repo 抽象层 + GitLab 适配器

| 项 | 内容 |
|---|---|
| **目标** | master 能调 GitLab API 拉仓库、读 commit、创建分支、commit 文件、创建 MR |
| **涉及文件** | `master/src/main/java/com/master/repo/RepoAdapter.java` (interface), `master/src/main/java/com/master/repo/gitlab/GitLabAdapter.java`, `master/src/main/java/com/master/repo/gitee/GiteeAdapter.java` (stub), `master/src/main/java/com/master/repo/RepoFactory.java` |
| **验收** | • interface 定义: `getRepo()`, `getFile()`, `createBranch()`, `commitFile()`, `createMR()`<br>• GitLabAdapter 用 `gitlab4j-api` 实现,所有方法跑通<br>• GiteeAdapter 返回 `UnsupportedOperationException("first issue: 详见 docs/CONTRIBUTING")`<br>• Factory 按 `project.repo_provider` 返回对应 adapter<br>• master 调 GitLab API 真实创建分支 + commit + MR 成功 |
| **测试** | • 单元测试: 各个方法 mock GitLab client<br>• 集成测试: Testcontainers 起 gitlab-local,真实跑通 commit + MR |
| **估算** | 2-3 天 |
| **依赖** | M2 |

---

### M5 - drone 集成

| 项 | 内容 |
|---|---|
| **目标** | master 能调 drone 触发构建 + 接收 webhook + HMAC 验签 |
| **涉及文件** | `master/src/main/java/com/master/build/DroneClient.java`, `master/src/main/java/com/master/build/WebhookController.java`, `master/src/main/java/com/master/build/HmacVerifier.java` |
| **验收** | • DroneClient 用 drone CLI wrapper(Java 调 `drone build create`)+ 封装<br>• WebhookController 接 POST `/webhook/drone`,返回 200<br>• HmacVerifier 验签:失败的请求 401 + 记 alert_log(P1)<br>• Webhook 处理 BuildRunning/BuildSuccess/BuildFailed 三种事件<br>• 同一 IP 5/min 验签失败触发 P0 告警(留 hook 给 M11 实现) |
| **测试** | • 单元测试: HMAC 伪造/重放/篡改 3 个 case<br>• 集成测试: Testcontainers drone-server + drone-runner,真实触发构建 |
| **估算** | 2-3 天 |
| **依赖** | M2 |

---

### M6 - pipeline 模板管理 + AI 改(mock)

| 项 | 内容 |
|---|---|
| **目标** | 流水线模板 CRUD + 版本管理 + AI 改(默认 mock) |
| **涉及文件** | `master/src/main/java/com/master/pipeline/`, `master/src/main/java/com/master/ai/`, `web/src/views/PipelineEdit.vue`(完整实现) |
| **验收** | • `pipeline_template` 表 CRUD<br>• 版本机制: 每次新建 version+1,is_active 默认 false,approved 后才 true<br>• `POST /api/projects/{id}/pipeline/ai-edit` 调用 MockLLMAdapter 返回新 yaml draft<br>• Web 端 PipelineEdit 页可编辑 yaml + 看 diff + apply<br>• 危险字符串黑名单: `rm -rf`, `curl | sh`, `chmod 777` 拒收 |
| **测试** | • 单元测试: 版本状态机(approved/rejected/draft)<br>• 单元测试: AI 危险字符串拦截<br>• E2E: Web 端改 yaml → apply → 列表显示 approved |
| **估算** | 2-3 天 |
| **依赖** | M3, M5 |

---

### M7 - env_variable + 注入 drone

| 项 | 内容 |
|---|---|
| **目标** | 环境变量 CRUD + AES 加密 + 注入 drone 构建 |
| **涉及文件** | `master/src/main/java/com/master/variable/`, `master/src/main/java/com/master/crypto/AesEncrypter.java`, `master/src/main/java/com/master/build/VariableInjector.java` |
| **验收** | • `env_variable` 表 CRUD<br>• AesEncrypter: AES-256/GCM,启动时读 `application.yml` 的 master key<br>• **envelope encryption 接口设计**: `Encrypter` interface,V1 实现 AesEncrypter,以后接 KMS 只换实现<br>• VariableInjector 拼 `vars.yaml` (解密后),不落盘,直接走 stdin 传给 drone<br>• 启动时全量校验变量完整性,启动失败列出损坏变量 |
| **测试** | • 单元测试: 加解密往返 + 启动完整性校验<br>• 集成测试: 真实 drone 跑构建,envvars 在 drone 步骤里能读到 |
| **估算** | 1-2 天 |
| **依赖** | M5 |

---

### M8 - worker Go 二进制

| 项 | 内容 |
|---|---|
| **目标** | worker 部署进 k3s 集群,能接 master 任务做 deploy/rollback/stop |
| **涉及文件** | `worker/go.mod`, `worker/main.go`, `worker/internal/handler/`, `worker/Dockerfile`(scratch) |
| **验收** | • Go 1.22+,`go build` 出单二进制 < 20MB<br>• scratch 镜像(只含 ca-certificates + 二进制)<br>• API: POST /api/v1/tasks/{deploy,rollback,stop}<br>• 调 k8s API 用 client-go + in-cluster ServiceAccount(最小权限)<br>• 失败返回结构化 JSON 错误码<br>• 部署后 5min 内 5s/次 report pod 状态 |
| **测试** | • 单元测试: deploy 状态机<br>• 集成测试: 真实 k3s 集群 apply deployment<br>• **PBT 用 jqwik... 不对,Go 用 gopter** — snapshot 拼装用 gopter(实际拼装在 master,见 M9)<br>• 镜像大小验证: < 20MB |
| **估算** | 2-3 天 |
| **依赖** | M2(API 端点定下来) |

---

### M9 - snapshot 拼装 + 回滚

| 项 | 内容 |
|---|---|
| **目标** | master 拼完整 deployment snapshot,deploy 用它,回滚用历史 snapshot |
| **涉及文件** | `master/src/main/java/com/master/deploy/SnapshotBuilder.java`, `master/src/main/java/com/master/deploy/DeployService.java` |
| **验收** | • SnapshotBuilder 输入: image tag + envvars(解密)+ replicas + resource limits + probes → 输出完整 yaml<br>• DeployService: 写 `deploy_record(snapshot_yaml=完整 yaml)`,调 worker apply<br>• 回滚: 选历史 deploy_record,取它的 snapshot_yaml 重 deploy<br>• **PBT 用 jqwik**: 输入任意组合,snapshot 能被 snakeyaml 解析 + 字段一致<br>• 并发保护: Redis 锁 + deploy_record 状态机防重 |
| **测试** | • **PBT 重点测**: special chars in envvars(`:` 换行 中文 超长),resource limits 边界,probe 路径含特殊字符<br>• 单元测试: 状态机<br>• 集成测试: 真实 k3s deploy + 回滚 |
| **估算** | 2-3 天 |
| **依赖** | M7, M8 |

---

### M10 - LLM adapter + 3 个 capability

| 项 | 内容 |
|---|---|
| **目标** | LLMAdapter 抽象 + Mock 实现 + 3 个 capability 接入 |
| **涉及文件** | `master/src/main/java/com/master/ai/LlmAdapter.java` (interface), `MockLlmAdapter.java`, `TongyiLlmAdapter.java`, `DiagnosisService.java`, `DecisionService.java` |
| **验收** | • interface: `String chat(String systemPrompt, String userPrompt)`<br>• MockLlmAdapter 返回预制 yaml/diagnosis/decision<br>• TongyiLlmAdapter 调通义 API(用 WebClient)<br>• 三个 capability 走同一 adapter:<br>　 1. PipelineGenService(接 M6)<br>　 2. DiagnosisService: 失败时自动 + 手动触发<br>　 3. DecisionService: 构建成功后自动 + 手动触发<br>• ai_interaction 表全量留痕(LLM request/response/采纳与否)<br>• LLM 失败 1 次重试(显式偶发场景) |
| **测试** | • 单元测试: Mock adapter 返回固定值<br>• 集成测试: Tongyi adapter(用 mock LLM 端点,Testcontainers 起 mock server)<br>• Web E2E: 失败 build → 点 AI 诊断 → 看到结果 |
| **估算** | 2-3 天 |
| **依赖** | M6 |

---

### M11 - 监控告警(先 log 后 webhook)

| 项 | 内容 |
|---|---|
| **目标** | master/worker 暴露 Prometheus metrics + alert_log 记录 + UI 展示 |
| **涉及文件** | `master/.../monitor/`, `worker/.../metrics/`, `web/src/views/Alerts.vue` |
| **验收** | • master `/actuator/prometheus` 返回 Micrometer 指标<br>• worker `/metrics` 返回 Prometheus 指标<br>• 关键指标: `build_total`, `build_duration_seconds`, `deploy_total`, `deploy_failed_total`, `ai_calls_total`, `llm_latency_seconds`<br>• alert_log 表 CRUD,level P0/P1/P2<br>• Web `/alerts` 页面列表 + ack/resolve 按钮<br>• **Grafana dashboard 模板**(JSON 文件)<br>• **不接 webhook**(V1 简化,V1.5 接) |
| **测试** | • 单元测试: alert 触发条件<br>• 集成测试: Prometheus 抓取成功,Grafana query 返回数据 |
| **估算** | 1-2 天 |
| **依赖** | M5, M9, M10 |

---

### M12 - Dockerfile 模板

| 项 | 内容 |
|---|---|
| **目标** | master 自带 4-5 套 Dockerfile 模板,生成后提交进项目仓库走 MR |
| **涉及文件** | `master/src/main/java/com/master/dockerfile/`, `master/src/main/resources/templates/*.mustache`, `web/src/views/CreateProject.vue`(完整), `web/src/views/DockerfileFlow.vue`(生成流) |
| **验收** | • 模板用 Mustache:`java_maven_jdk17.mustache` / `java_gradle.mustache` / `node_pnpm.mustache` / `python_poetry.mustache`<br>• dockerfile_template 表存 4 套种子数据(SQL 初始化)<br>• 生成: 模板 + 变量 → 渲染 → 预览 → 推仓库(走 M4 repo adapter)<br>• 走 MR 不直接推 main<br>• AI 改变量: POST /ai-regenerate,改变量值后重渲染 |
| **测试** | • 单元测试: 模板渲染(各种变量组合)<br>• 集成测试: Testcontainers gitlab,真实推 Dockerfile + 创建 MR |
| **估算** | 2-3 天 |
| **依赖** | M4, M6 |

---

### M13 - 实时日志 SSE

| 项 | 内容 |
|---|---|
| **目标** | master 实时转发 drone 日志给前端,构建完落 MySQL |
| **涉及文件** | `master/src/main/java/com/master/build/LogStreamController.java`, `master/src/main/java/com/master/build/LogPersistenceService.java`, `web/src/views/BuildDetail.vue`(完整) |
| **验收** | • SSE endpoint: GET `/api/builds/{id}/logs/stream` 返回 `SseEmitter`<br>• master 用 WebClient 调 drone stream API 转发<br>• 持久化: webhook 推 BuildSuccess/Failed 时,master 同步调 drone logs API 写 `build_log` 表<br>• 完整日志: GET `/api/builds/{id}/logs` 返回已持久化日志<br>• Web BuildDetail 页用 EventSource 接收,失败时弹 AI 诊断按钮 |
| **测试** | • 单元测试: SSE 连接生命周期<br>• 集成测试: Testcontainers drone,真实 build 过程中 SSE 能收到日志<br>• 集成测试: 完成后从 MySQL 查 build_log 有完整内容 |
| **估算** | 2-3 天 |
| **依赖** | M5, M9 |

---

### M14 - CI/测试/质量门

| 项 | 内容 |
|---|---|
| **目标** | GitHub Actions 跑通 + 覆盖率门槛 + Apache 2.0 header + commit 规范 |
| **涉及文件** | `.github/workflows/ci.yml`, `.github/workflows/release-please.yml`, `.github/dependabot.yml`, `pom.xml` 配置 spotless, `web/` 配置 prettier, `commitlint.config.js` |
| **验收** | • CI workflow 跑: 单元测试 + 集成测试 + lint + coverage 报告 + hadolint<br>• 覆盖率门槛: master 后端 ≥ 70%, worker ≥ 60%, 前端 ≥ 50%(PR 合入要求)<br>• spotless + prettier pre-commit hook<br>• Conventional Commits + commitlint<br>• 每个文件 Apache 2.0 header(用 spotless plugin)<br>• Dependabot 自动更新 PR<br>• release-please 自动生成 CHANGELOG |
| **测试** | • 验证: 故意提个 coverage 不够的 PR,看到 CI 失败 |
| **估算** | 1-2 天 |
| **依赖** | M2, M8(M11 也可) |

---

### M15 - demo 编排周

| 项 | 内容 |
|---|---|
| **目标** | `make demo` 一键起全部组件 + demo 视频 + README + interview-prep |
| **涉及文件** | `docker-compose.yml`(完整版), `demo/Makefile`, `demo/k3s-init.sh`, `demo/init-data.sh`, `docs/architecture.md`(完整), `docs/demo/*.mp4`, `docs/interview-prep.md`, `README.md` + `README.zh-CN.md`(完整) |
| **验收** | • 一键拉起: master + MySQL + Redis + drone + drone-runner + Harbor + k3s + worker(k3s 内部) + Prometheus + Grafana<br>• 演示脚本: 5 分钟 demo 视频(屏幕录制)<br>• 关键截图 5-6 张<br>• README 完整(中英双语)+ 架构图(4 张 mermaid)+ Quick Start<br>• docs/interview-prep.md(Mavis 帮整理): 5 分钟讲稿 + 20 个 Q&A + 3 个踩坑故事<br>• CONTRIBUTING.md 写清楚 first issue(Gitee 适配器) |
| **测试** | • 端到端: 干净机器 clone + make demo → 跑通演示流程<br>• Playwright E2E: 录视频同时跑 Playwright 验证 |
| **估算** | 5-7 天(整周) |
| **依赖** | M1-M14 全部 |

---

### M16 - Release v0.1.0

| 项 | 内容 |
|---|---|
| **目标** | GitHub 上有 v0.1.0 release,GHCR 推 Docker image,可在简历上列 |
| **涉及文件** | (release-please 自动生成) |
| **验收** | • 推 v0.1.0 tag<br>• GitHub Release 自动发布<br>• Docker image 推 GHCR<br>• CHANGELOG 自动生成<br>• 在社交媒体发"刚开源了 XX"链接 |
| **测试** | • 别人 clone 后能跑通 |
| **估算** | 0.5 天 |
| **依赖** | M15 |

---

## 3. 时间线总表

| Week | 里程碑 | 工时合计 | 关键交付 |
|---|---|---|---|
| 1 | M1 + M2 + M3 + M4 | ~7-9 天 | 仓库骨架 + 后端能起 + 前端能跑 + repo 抽象层 |
| 2 | M5 + M6 + M7 | ~5-7 天 | drone 集成 + pipeline 管理 + 变量管理 |
| 3 | M8 + M9 + M10 + M11 | ~7-9 天 | worker + snapshot + AI 三能力 + 监控 |
| 4 | M12 + M13 + M14 | ~5-7 天 | Dockerfile + 实时日志 + CI 门 |
| 5-6 | M15 + M16 | ~5-7 天 | demo 编排 + release |

**总计 5-6 周**(30-40 工作日)

---

## 4. 风险与回退

| 风险 | 触发条件 | 回退方案 |
|---|---|---|
| **M5 drone 集成卡住** | drone 1.x API 不稳定或文档不全 | 切到 Tekton(用同样的 master 调 API 模式) |
| **M8 worker 镜像太大** | scratch 镜像 > 20MB | 改用 distroless 静态镜像 |
| **M9 PBT 找不到合适 generator** | jqwik Arbitrary 写不出 | fallback 到密集 example-based case,牺牲覆盖率 |
| **M10 通义 API 限流** | 调外部 LLM 频繁 429 | 加重试退避 + 默认 mock 兜底 |
| **M13 SSE 在 k3s 里不稳** | 长连接断 | 加心跳 + 自动重连 |
| **M15 demo 跑不通** | 多组件依赖问题 | 拆分启动,提供"分阶段启动"脚本 |

---

## 5. 下一步

1. **逐 milestone 实施**: 每个 M 完成后 commit + 自测 + 更新本 plan 文件的"状态"列
2. **每周一 review**: 当前周 M 是否完成,下周 M 是否要调整
3. **M15 触发条件**: M1-M14 全部完成才能进 demo 周
4. **遇到卡点** (超过估算 1.5 倍时间): 找 Mavis 讨论调整,不硬扛

---

**Plan 版本**: 0.1.0
**配套文档**:
- spec: `docs/superpowers/specs/2026-08-08-platform-design.md`
- wireframes: `docs/superpowers/wireframes/index.html`
- HTML spec: `docs/superpowers/specs/2026-08-08-platform-design.html`
