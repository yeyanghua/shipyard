# shipyard — 项目进度

> **TL;DR**: V1 横向 demo 阶段。已推 GitHub (`yeyanghua/shipyard`)。
> **M1 + M2 + M2.5 (SecurityConfig 合并) + M3 + M4 + M5 + M6 1/2/3/4 + M7 polish + M12 + M13 全部完成**。
> k8s 阶段 0~3 完成 (3 台 Rocky 9.6 VM, k8s 1.31, Calico v3.28). V1 主体约 78% 完成.
> M13: 引入 Element Plus + 全局命令面板 (Cmd+K) + Monitoring/Activity/Notifications 3 个核心页面 + 明暗主题切换.
> **换电脑/换设备** → 看本文 §6「换设备恢复步骤」。

| 字段 | 值 |
|---|---|
| GitHub | https://github.com/yeyanghua/shipyard |
| 当前分支 | `main` |
| 当前 commit | `49f532b` (M13 Phase 2c — 明暗主题切换) |
| 当前 milestone | **M1-M12 全部完成 + M13 阶段 1-2 全部完成 ✅** |
| 总 commit | 36 (35 + M13) |
| V1 整体 | 5-6 周(3-4 周主体 + 1-2 周 demo 编排), 主体约 78% 完成 |
| 上次更新 | 2026-08-09 (D 盘开发, M13 主题切换 + 4 个 phase commit) |

---

## 1. 当前状态

✅ **已完成**:
- 11 条核心需求 + 14 个 ask_user 决策(全部 spec 化)
- 完整设计 spec (35KB, 23 个决策, 8 张 Mermaid 图)
- 实现计划 (18KB, 16 个 milestone, 5-6 周)
- 8 个核心页面 wireframes (63KB, 浏览器可看)
- **M1** 仓库骨架 (16 个文件)
- **M2** shipyard 后端骨架 (commit `063970a`) - Spring Boot 3.2.5 + Java 21 + 13 张表 Flyway + AesEncrypter + 虚拟线程
- **M2.5** Security 合并 (commit `28fb602`) + 启动崩溃修复 (commit `85dcc05`)
- **M3** Web 端骨架 (commit `5851894`) - Vue 3.4 + TS 5.5 + Vite 5.4 + 8 页面占位 + 6 测试
- **M4** 前后端贯通 (commits `81f4c99` `1e0f7cd` `88a22e2` `25e37ea`) - 4 Entity + 4 Mapper + 4 Service + 4 Controller + 10 DTO + 公共异常 + Hard-coded JWT 鉴权; 14 业务端点 + /api/auth/demo-token; E2E 20/20 通过
- **M5** 端到端可演示 (commits `3fac032` `fe17315` `2cf2b44` `bcb69c4` `6be779e` `1e1d9cb` `376f40e`) - drone 集成 mock + SSE 实时日志 + 环境变量注入 + 前端 BuildDetail 实时 UI
- **M6 1** pipeline_template 后端数据层 (commits `5157f1c` `18d0765`) - 3 枚举 (ReviewStatus/AiCapability/LlmProvider) + 2 实体 (PipelineTemplate/AiInteraction) + 2 Mapper + Service 业务规则 (版本自增/active 唯一/approved immutable) + 20 单元测试
- **M6 2** AI 集成 (commits `0aa80ef` `052b294`) - LlmAdapter interface + 3 实现 (Mock/Tongyi/Deepseek) + 3 capability handler (PipelineGen/Diagnosis/Decision) + AiInteractionService 落痕 + PromptSanitizer 脱敏 + 48 单元测试
- **M6 4** PipelineController + BuildService 集成 (commit `96dbf47`) - 7 端点 + 4 DTO + createBuild 自动绑 active pipeline + force delete (V1 demo) + 1 单元测试
- **M6 3** 前端 PipelineEdit 页 (commit `8279e3f`) - 8 端点 api client + 重写 PipelineEdit.vue (YAML 编辑器 + AI 改按钮 + 行级 LCS diff) + ProjectDetail 卡片 + 顺手修 M3 留下的 lint config (flat config → traditional .eslintrc.cjs)
- **k8s 阶段 0~3** 3 台 Rocky 9.6 VM 集群 (k8s-master/node1/node2 全 Ready) - 阿里云 docker-ce + 阿里云 pause 镜像 + quay.io calico + VPN 代理 192.168.10.29:7890 - 完整步骤 + 11 条踩坑合集见 `docs/K8S-DEPLOY.md`
- **docs/K8S-DEPLOY.md** (commit `d05f63e`) - 539 行 k8s 部署复习文档
- **M7 polish** (commit `d1add71`) - EnvVars UI 4 个增强: .env 批量导入 + K8s Secret YAML 预览 + key 冲突检查 + 搜索过滤
- **M12 Dockerfile 模板** (commit `16e3b87`) - 5 套内置模板 (java_maven_jdk21 / java_gradle_jdk21 / node_pnpm_20 / python_poetry_312 / generic_alpine) + 启动时幂等插入 + Service 渲染 (${var} 替换) + 3 端点 (list/preview/generate) + 前端 ProjectDetail 卡片 + 动态变量表单 + 实时预览
- **M13 前端全面升级** (commits `30aa294` `c37c17d` `fc6df2e` `49f532b`) - 引入 Element Plus + 暗色主题深度覆盖 + 全局命令面板 (Cmd+K Linear 风格) + 3 个新页面 (Monitoring/Activity/Notifications) + 明暗主题切换 (Pinia + localStorage 持久化 + system 跟随)
- **D 盘验证**: mvn test 113/113 (9 AesEncrypter + 14 HmacVerifier + 22 PipelineTemplate + 48 M6 2 + 12 DockerfileTemplate + 8 ProjectDockerfile) + test-m5-2 13/13 + test-m5-5 8/8 + test-m6-4 18/18 + test-m6-3 13/13 + test-m12 11/11 + pnpm typecheck 0 errors + pnpm lint 0 errors 0 warnings (max-warnings 0)

⏳ **下一步**: M8 — worker Go (k8s deploy worker, 3-5 天) 或 M9 snapshot + 回滚 (1-2 天) 或 M11 监控告警 (1-2 天) 或 M13 Phase 4 (ProjectDetail Tabs + 5 页 polish, 1-2 天)

---

## 2. GitHub 状态

```
仓库:    https://github.com/yeyanghua/shipyard
remote:  git@github.com:yeyanghua/shipyard.git (SSH)
分支:    main (default, 受保护建议)
克隆:    git clone git@github.com:yeyanghua/shipyard.git
```

### 25 个 commit 历史 (M1 → M5)

| SHA | 说明 |
|---|---|
| `4fa2c81` | 统一构建发布平台设计 spec (V1 横向 demo 优先) |
| `1203393` | 加 spec HTML 版本(带 Mermaid 渲染 + GitHub 风格) |
| `6925e85` | 加 Dockerfile 自动生成功能(master 自带模板 + 提交进仓库) |
| `e9a22fa` | 加实时构建日志 + 修 mermaid 渲染 |
| `7578bff` | 修 mermaid flowchart 语法错误 |
| `c307cf3` | Java 17 升 21 LTS + 启用虚拟线程(Project Loom) |
| `884d990` | 加 8 个核心页面 wireframes(低保真原型) |
| `067c427` | 加 V1 实现计划(16 个 milestone, 5-6 周) |
| `367fbe2` | 仓库骨架(M1) + Apache 2.0 + 双语 README + 社区基础设施 |
| `fb0b57b` | 全套改名 master → shipyard(避免 master 分支/服务名/仓库名混淆) |
| `6fbbf6c` | docs: 加 PROGRESS.md(换设备无缝继续的接力棒) |
| `063970a` | M2: shipyard 后端骨架 (Spring Boot 3.2.5 + Java 21 + 13 张表 Flyway V1 + AesEncrypter 9 测试 + 虚拟线程) |
| `5851894` | M3: shipyard Web 前端骨架 (Vue 3.4 + TS 5.5 + Vite 5.4 + 8 页面占位 + 6 测试) |
| `28fb602` | 合并 master/ → shipyard/ + SecurityConfig/JwtAuthFilter(JWT 白名单) + 删 1182 行重复 |
| `85dcc05` | M2.5 启动崩溃修复:SecurityConfig 用 JwtProperties(@ConfigurationProperties) 替代 @Value,YAML list 解析修好,本机 13 张表落库 + health UP 全验 |
| `81f4c99` | M4 后端 1+2:4 Entity + 4 Mapper + 3 Service (Project/Env/ProjectEnv) + 公共异常 (BusinessException/ErrorCode) |
| `1e0f7cd` | M4 后端 3:EnvVariableService (加密/解密/resolve/validate) + 启动加密健康检查 |
| `88a22e2` | M4 后端 4:4 Controller + 10 DTO + GlobalExceptionHandler + /api/auth/demo-token + BeanUtils 工具 |
| `25e37ea` | M4 前端对接:4 API + 3 store + 5 页面 + 2 组件 + App.vue 自动拉 token; 端到端跑通 |
| `3fac032` | **M5 1**:Spring Security 6.2 鉴权 bug workaround (demo-mode) + KNOWN_ISSUES.md |
| `fe17315` | **M5 2.1**:BuildRecord + BuildLog 实体/Mapper + BuildStatus/TriggerType 枚举 |
| `2cf2b44` | **M5 2.2**:BuildService + MockDroneClient (虚拟线程异步) + HMAC 验签 (14 单元测试) |
| `bcb69c4` | **M5 2.3**:BuildController + DroneWebhookController + E2E 13/13 |
| `6be779e` | **M5 3**:SSE 实时日志接口 (BuildLogNotifier + /api/builds/{id}/stream) |
| `1e1d9cb` | **M5 5**:环境变量注入 drone (EnvVariableService.resolveAll 进 build 流程) |
| `376f40e` | **M5 6**:Web BuildDetail 实时日志 UI + ProjectDetail 触发构建 + build 历史 |

---

## 3. 已完成 milestone

### M5 — drone 集成 + SSE 实时日志 (7 commit, 8/9 10:00 ~ 8/9 12:10)

**M5 1 — 鉴权 bug workaround** (commit `3fac032`)
- 原因: Spring Security 6.2.4 + Boot 3.2.5 下 `.authorizeHttpRequests()` lambda 规则未注册到 AuthorizationFilter, 试过 6 种方案无效
- V1 workaround: `shipyard.security.demo-mode=true` 默认全 permitAll, JwtAuthFilter 仍跑 (解析 JWT 写 context 但不强制)
- V1.5 修复路径 (3 选 1): 升 Security 6.3 / 降 6.1 / 写自定义 AuthorizationFilter, 详见 `docs/KNOWN_ISSUES.md`
- 文档化: `docs/KNOWN_ISSUES.md` KI-001 完整记录根因 + 失败方案 + V1.5 plan

**M5 2 — drone 集成 mock** (commits `fe17315` `2cf2b44` `bcb69c4`)
- 后端: 2 Entity (BuildRecord/BuildLog) + 2 Mapper (含 markRunning/markFinished 条件 SQL) + BuildService + MockDroneClient (Java 21 虚拟线程异步跑 3 step) + HmacSigner/HmacVerifier + BuildController (7 端点) + DroneWebhookController (HMAC 验签)
- 4 个 enum 状态机: PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELED + isTerminal()
- 14 单元测试覆盖 HMAC 验签 (伪/篡改/重放/边界)
- E2E 13/13: 触发→mock 跑 3 step→终态 SUCCESS→HMAC 验签 3 case

**M5 3 — SSE 实时日志** (commit `6be779e`)
- 后端: `BuildLogNotifier` (@Component, 内存订阅表 `Map<buildId, CopyOnWriteArrayList<SseEmitter>>`)
- `GET /api/builds/{id}/stream` SSE endpoint (text/event-stream)
- 事件类型: `event:step` (BuildLogEvent.step*) + `event:build` (终态, 推完自动 complete)
- curl -N 验证: 收 4 事件 (3 step + 1 build SUCCESS)

**M5 5 — 环境变量注入 drone** (commit `1e1d9cb`)
- `BuildCreateRequest` 加 `envId` 字段 (Long, 选填)
- `BuildService.createBuild` 调 `EnvVariableService.resolveAll(envId, projectId)`, envVars 塞 `DroneBuildRequest.envVars`
- 三档回退: envId 显式 > project 第一个关联 env > 空 map (V1 demo 接受)
- E2E 8/8: 建 env + projectEnv + 2 var (1 明文 1 secret) → trigger → mock drone 收到 envVars=[JAVA_HOME, DB_PASSWORD]

**M5 6 — Web BuildDetail 实时日志 UI** (commit `376f40e`)
- `web/src/api/builds.ts` (新) + `web/src/stores/build.ts` (Pinia store, EventSource 生命周期) + `web/src/views/BuildDetail.vue` (重写)
- BuildDetail: 左侧元信息 + step 列表 (状态色 + 耗时) / 右侧 log 区 (自动滚到底) + 取消按钮 + SSE 连接状态
- `ProjectDetail.vue` 加 [🚀 触发构建] 按钮 + 表单 (commit SHA + env 选择) + 底部 build 历史表格
- 路由 `/builds/:id` milestone M11 → M5
- typecheck 0 errors + build 729ms + BuildDetail chunk 5.13 KB
- SSE 走 `sseBaseURL` (直连 8080, 绕开 Vite proxy 对 SSE 的兼容问题)

### M4 — 项目/环境/变量 CRUD (4 commit, 8/9 上午完成)

- 4 Entity (Project/Env/ProjectEnv/EnvVariable) + 4 Mapper + 4 Service + 4 Controller + 10 DTO
- 14 业务端点 + `/api/auth/demo-token` (Hard-coded JWT, 7 天有效, role=admin)
- 折中加密: 3 secret 字段加密 (env_variable.value / project.repo_token / env.worker_token), 其他明文
- 软删 + 复活: `@TableLogic` + raw SQL (`selectIdByNameRaw` / `selectByIdIncludeDeleted`)
- 业务码独立错误体系: HTTP 永远 200, body `{code: 0=成功}`
- EnvVariable 启动时全量校验 (CryptoHealthCheck) - 损坏变量阻止启动
- 前端 5 页面 + 2 组件 + 3 store + 4 API + 鉴权自动注入
- E2E 20/20 通过 (`logs/test-m4.ps1`)

### M3 — Web 前端骨架 (commit `5851894`)

- Vue 3.4 + TypeScript 5.5 + Vite 5.4 + pnpm 11.20 + Pinia 2.2 + Vue Router 4 + Axios 1.7 + Vitest 2.0
- 8 个核心页面占位
- Vite dev proxy `/api` → `http://localhost:8080`
- Axios 客户端: 拦截器归一化响应 `{code, message, data}`, 业务错误抛 `ApiError`
- 6 个测试通过

### M2 — shipyard 后端骨架 (commit `063970a`)

- Spring Boot 3.2.5 + Java 21 LTS + MyBatis-Plus 3.5.5 + Flyway 9.22.3 + MySQL 8 + Lombok + JUnit 5
- 13 张表 Flyway V1 migration 落库
- `AesEncrypter` (AES-256-GCM) + 9 个加解密单元测试
- `VirtualThreadConfig` (4 处虚拟线程: Tomcat / MVC 异步 / @Async / 命名 thread pool)
- `DataSourceConfig` (MyBatis-Plus 分页 + 乐观锁)
- `application.yml` (本地 MySQL/Redis 配, prod profile 强制 env var 注入)

### M1 — 仓库骨架 (commit `367fbe2`)

- 16 个文件: LICENSE (Apache 2.0) + 双语 README + CONTRIBUTING + CODE_OF_CONDUCT + SECURITY + CHANGELOG + Makefile + docker-compose + docs/architecture + 5 个 .github 模板

---

## 4. 下一步: M6 — Pipeline 编辑 + AI 改/生成

**目标**: shipyard 端存 pipeline_template, Vue 端 PipelineEdit 页可改, AI (mock LLM 默认) 一键生成.

✅ **M6 1 — pipeline_template 后端数据层** (commits `5157f1c` `18d0765`, 2026-08-09)
- 3 枚举: ReviewStatus (draft/approved/rejected) + AiCapability (pipeline_gen/diagnosis/decision) + LlmProvider (mock/tongyi/deepseek)
- 2 实体: PipelineTemplate (不继承 BaseEntity, 表没 updated_at) + AiInteraction (不可变流水表, 没 deleted)
- 2 Mapper: PipelineTemplateMapper (max version 自增 / active 唯一 / unactivateOthers 事务) + AiInteractionMapper
- PipelineTemplateService: 业务规则 (版本自增 / approved immutable / 一项目一 active / active 必须 approved / 删除约束)
- 20 单元测试全过, mvn test 43/43 总数
- E2E 留到 M6 4 controller 加完后一起做

⏳ **M6 2 — AI 集成** (MockLLMAdapter 默认, 下个开工)
- LlmAdapter interface + 3 实现: MockLlmAdapter (默认) / TongyiLlmAdapter / DeepseekLlmAdapter
- 3 capability 各自的 prompt 模板 + response 解析
- ai_interaction 表自动落痕 (含脱敏)
- 默认 mock, 真 LLM 走 TONGYI_API_KEY / DEEPSEEK_API_KEY env var

**M6 4 — PipelineTemplate Controller** (后端, 跟 M6 2 一起)
- `GET /api/projects/{id}/pipeline` 当前 active 版本
- `GET /api/projects/{id}/pipeline/versions` 列出所有版本
- `POST /api/projects/{id}/pipeline` 创建新版本 (含 AI 生成入口)
- `PUT /api/projects/{id}/pipeline/{versionId}` 更新 (draft only)
- `POST /api/projects/{id}/pipeline/{versionId}/approve` 审批
- `POST /api/projects/{id}/pipeline/{versionId}/reject` 驳回
- `POST /api/projects/{id}/pipeline/{versionId}/activate` 激活

**M6 3 — PipelineEdit 页** (前端, M6 4 后做)
- YAML 编辑器 (textarea + 实时校验) + AI 改按钮 + diff 展示
- Web AI 页面占位先, M12 接真 LLM

**M6 5 — 接入真实 LLM** (略, V1 demo 不做, M12 接)

**剩余工时**: 2-3 天 (M6 2 + M6 4 + M6 3)

---

## 5. 关键决策速查 (23 条)

完整 23 条决策见 `docs/superpowers/specs/2026-08-08-platform-design.md` §11。索引:

| # | 决策点 | 选择 |
|---|---|---|
| 1 | AI 方向 | 流水线生成 + 故障诊断 + 发布决策 全做 |
| 2 | drone 集成 | shipyard 调 drone API(UI 隐藏) |
| 3 | 多环境拓扑 | 每环境独立 k8s 集群 |
| 4 | 环境变量 | shipyard 自存 + AES-256 加密 |
| 5 | 仓库平台 | 多平台(GitLab V1 实现, Gitee V1 stub) |
| 6 | 流水线配置 | shipyard 自存 + AI 改完用户 review |
| 7 | 构建 vs 发布 | 显式两步 |
| 8 | 回滚 | shipyard 记 deployment snapshot |
| 9 | AI 集成 | 调外部 LLM API (Tongyi/DeepSeek) |
| 10 | 监控告警 | Prometheus + Alertmanager + alert_log |
| 11 | V1 范围 | 横向 demo 优先 |
| 12 | demo 部署 | docker-compose + k3s (开发阶段不写, demo 周写) |
| 13 | V1 仓库适配器 | 只实现 GitLab, 抽象层留好 |
| 14 | LLM demo 模式 | 默认 mock, 真 LLM 开关切换 |
| 15 | LICENSE | Apache 2.0 |
| 16 | README | 中英双语 |
| 17 | 架构图 | Mermaid |
| 18 | interview-prep | V1 收尾 Mavis 整理 |
| 19 | Dockerfile 模板 | shipyard 自带 4-5 套主流 |
| 20 | Dockerfile 存储 | 提交进项目仓库(走 MR) |
| 21 | 实时构建日志 | 实时 + shipyard 持久化 |
| 22 | 实时日志 UI | 构建详情页 |
| 23 | Java 版本 | Java 21 LTS + 虚拟线程 |

---

## 6. 换设备恢复步骤

> **新电脑从零开始**: 跟着这个清单走, 30 分钟内能进入 M6 实施

### 6.1 装环境

```bash
- JDK 21 LTS (Eclipse Temurin 推荐: https://adoptium.net/)
- Go 1.22+ (https://go.dev/dl/)
- Node.js 20 LTS + pnpm 9.x
- Maven 3.9+
- Docker 24+ + Docker Compose 2.x
- Make (Windows 用 Git Bash 或 WSL)
- k3d (demo 集群用: https://k3d.io/)
- Python 3.x (生成 HTML spec)
```

### 6.2 配 Git

```bash
# 项目级 git config(覆盖全局)
git config user.name "仔哥"
git config user.email "470842725@qq.com"
```

### 6.3 配 SSH key 到 GitHub

如果新电脑没有 SSH key:
```bash
ssh-keygen -t ed25519 -C "470842725@qq.com"
# 复制 ~/.ssh/id_ed25519.pub 到 GitHub Settings → SSH and GPG keys
```

### 6.4 克隆 + 启动

```bash
# 克隆
git clone git@github.com:yeyanghua/shipyard.git
cd shipyard

# 起基础设施(只起 MySQL + Redis)
make infra

# 在另一个终端起 shipyard 后端
cd shipyard
export MYSQL_PASSWORD=123456  # PowerShell: $env:MYSQL_PASSWORD='123456'
mvn spring-boot:run

# 在另一个终端起 web 前端
cd web
pnpm install
pnpm dev
```

### 6.5 告诉 Mavis "我回来了"

新电脑/新会话打开 Mavis, **第一句话** 引用本文 + 当前 commit:

> "回到 shipyard 项目, 仓库在 yeyanghua/shipyard, 当前 commit `376f40e` (M5 6 前端 BuildDetail), M1 + M2 + M2.5 + M3 + M4 + M5 全部完成, M6 (Pipeline 编辑 + AI 改) 准备开始。"

Mavis 会自动:
- 读 `PROGRESS.md` 知道进度
- 读 `docs/superpowers/specs/2026-08-08-platform-design.md` 知道设计
- 读 `docs/KNOWN_ISSUES.md` 知道鉴权 bug workaround
- 读 `docs/superpowers/plans/2026-08-08-platform-implementation.md` 知道下一步
- 不会问"你之前做了啥"

---

## 7. 工作风格提示(给未来的 Mavis 提示)

读这段的 Mavis 应该按这个用户风格来沟通:

| 习惯 | 说明 |
|---|---|
| **委派式** | 用户给方向 + 拍板, Mavis 给方案 + 执行 + 状态。**不要等用户每步确认**。 |
| **教学详细注释** | Java 后端背景, 教学风格 = Java/Spring 类比 + 代码详细注释 + 分 milestone 推进。 |
| **私活=真实甲方** | 这项目要开源 + 面试用, 质量按上线级(测试/部署/安全/错误处理都要正式)。 |
| **跨设备继续** | **切电脑一定要无缝继续**。所有进度沉 git/PROGRESS.md。新电脑 clone + 看 PROGRESS.md 就能继续。 |
| **沟通风格** | 短句 + 表情 + 直接判断, 不啰嗦。给方案 B 不给 ABC 全部。 |
| **隐私** | 真实邮箱 `470842725@qq.com` 不出现在 README/spec 文档, 用占位 `*@shipyard.dev`。 |
| **演示报告留档** | 长链路推进后, 把截图+文字沉到 `docs/demo/` 或 `PROGRESS.md`。 |

---

## 8. 文档索引(找东西用)

```
PROGRESS.md                                                ← 你正在看
README.md                                                  ← 仓库入口
README.zh-CN.md                                            ← 仓库入口(中文)

docs/
├── architecture.md                                        ← 架构总览(stub, M2 后更新)
├── KNOWN_ISSUES.md                                        ← 已知问题 + workaround (M5 加)
├── DEPLOY.md                                              ← M5 demo 部署版本清单
├── DEPLOY-ENV.md                                          ← M15 真实环境组件版本清单
├── K8S-DEPLOY.md                                          ← k8s 1.31 集群部署全流程 + 11 踩坑 (M6 3 后加)
├── demo/                                                  ← (M15 加 demo 视频/截图)
├── interview-prep.md                                      ← (M15 加, 5分钟讲稿 + Q&A)
└── superpowers/
    ├── specs/
    │   ├── 2026-08-08-platform-design.md                   ← 完整设计 spec(35KB)
    │   └── 2026-08-08-platform-design.html                 ← 浏览器版 spec(70KB, Mermaid 渲染)
    ├── plans/
    │   └── 2026-08-08-platform-implementation.md           ← 16 milestone 计划(18KB)
    └── wireframes/
        └── index.html                                      ← 8 页面 wireframes(63KB)

shipyard/                                                  ← 后端 Spring Boot 3.2 + Java 21
web/                                                       ← 前端 Vue 3.4 + TS 5.5
worker/                                                    ← (M8 创建)
logs/                                                      ← E2E 脚本 + 启动 log (本地, 不入 git)

scripts/
├── build_spec_html.py                                     ← spec.md → HTML 工具
└── rename_to_shipyard.py                                  ← 改名脚本(留作参考)

.github/                                                   ← 社区模板 + CI
├── ISSUE_TEMPLATE/
├── workflows/                                             ← (M14 加 CI workflow)
├── dependabot.yml
└── CODEOWNERS
```

---

## 9. 常见任务速查

### 改完 spec.md 后重新生成 HTML
```bash
python scripts/build_spec_html.py docs/superpowers/specs/2026-08-08-platform-design.md docs/superpowers/specs/2026-08-08-platform-design.html
```

### 起后端 (PowerShell)
```powershell
$env:MYSQL_PASSWORD='123456'
cd D:\Projects\shipyard\shipyard
& 'D:\apache-maven-3.8.9\bin\mvn.cmd' spring-boot:run
```

### 起前端 (PowerShell)
```powershell
cd D:\Projects\shipyard\web
& 'C:\nvm4w\nodejs\node.exe' ./node_modules/vite/bin/vite.js --port 5179
```

### 跑 M5 E2E 回归
```powershell
cd D:\Projects\shipyard
powershell -ExecutionPolicy Bypass -File logs\test-m5-2.ps1   # 13/13 drone 集成
powershell -ExecutionPolicy Bypass -File logs\test-m5-5.ps1   # 8/8 env vars
```

### 触发一个 build + 看实时 SSE
```powershell
# 触发
$body = @{ projectId=1; commitSha='m5-demo-123'; triggeredBy='me@shipyard.dev' } | ConvertTo-Json
Invoke-RestMethod -Uri 'http://localhost:8080/api/builds' -Method Post -Body $body -ContentType 'application/json'

# 订阅 SSE (curl 走全 URL, 不用 Vite proxy)
& curl.exe -N -m 12 http://localhost:8080/api/builds/{id}/stream
```

---

## 10. 下次工作的第一句话模板

切电脑/换设备/隔天继续, 跟 Mavis 说:

> "回到 shipyard 项目, 当前 commit `376f40e` (M5 6 前端 BuildDetail), M1 + M2 + M2.5 + M3 + M4 + M5 全部完成, **M6 开始** (Pipeline 编辑 + AI 改)。"

或者:

> "回到 shipyard, 先做 [M6 某个子任务] / [改 spec 某个地方] / [看 wireframe] / [跑 demo]"

Mavis 读 PROGRESS.md + spec + plan + KNOWN_ISSUES 自动接上, 不需要你解释"做了啥"。

---

## 11. M2.5 + M5 完整验证记录 (2026-08-09, D 盘)

### D 盘环境
- **OS**: Windows 11, PowerShell
- **JDK**: 21.0.6 (Oracle) at `D:\jdk-21.0.6`
- **Maven**: 3.8.9 at `D:\apache-maven-3.8.9\bin\mvn.cmd`
- **MySQL**: 8.4.5 (服务 `MySQL84`, 端口 3306, 密码 `123456`)
- **Redis**: 在跑 (端口 6379, 无密码)
- **Node**: 22.13.0 (用 nvm, 切到 22.13.0 才能跑 pnpm 11)
- **Pnpm**: 11.20.0
- **项目根**: `D:\Projects\shipyard` (从 GitHub `yeyanghua/shipyard` clone)

### M2.5 验收清单
| 项 | 结果 | 备注 |
|---|---|---|
| `mvn compile` | ✅ | BUILD SUCCESS |
| `mvn test` (9 个 crypto 测试) | ✅ | AesEncrypter 全部通过 |
| 创建 `shipyard` 库 | ✅ | `CREATE DATABASE shipyard DEFAULT CHARACTER SET utf8mb4` |
| `mvn spring-boot:run` | ✅ | 2.49s 启动 |
| Flyway V1__init.sql | ✅ | 134ms 落 13 张业务表 + flyway_schema_history |
| 13 张业务表存在 | ✅ | ai_interaction, alert_log, build_log, build_record, deploy_record, dockerfile_template, env, env_variable, pipeline_template, project, project_dockerfile, project_env, worker |
| `/actuator/health` | ✅ | `{"status":"UP","groups":["liveness","readiness"]}` |
| GitHub push | ✅ | commit `85dcc05` → origin/main |

### M5 验收清单
| 项 | 结果 | 备注 |
|---|---|---|
| `mvn compile` | ✅ | 52 文件编译过 |
| `mvn test` (23 单元测试) | ✅ | 9 AesEncrypter + 14 HmacVerifier |
| `mvn spring-boot:run` | ✅ | 启动正常, demo-mode=true 跑通 |
| `/actuator/health` | ✅ | UP |
| `POST /api/builds` (PENDING) | ✅ | droneBuildId UUID, status=PENDING |
| Mock drone 跑 3 step | ✅ | compile (3s) → test (3s) → docker-push (3s) |
| `GET /api/builds/{id}` (终态 SUCCESS) | ✅ | imageTag + harborImageUrl + logPersisted=1 |
| `GET /api/builds/{id}/steps` (3 step) | ✅ | compile/test/docker-push |
| `POST /api/builds/{id}/cancel` (终态) | ✅ | code=400, 业务错误 |
| `POST /api/builds/{id}/cancel` (PENDING) | ✅ | CANCELED |
| `POST /webhook/drone` 错签 | ✅ | code=401, HMAC 验签失败 |
| `POST /webhook/drone` 真签+未知 build | ✅ | code=404 |
| `POST /webhook/drone` 真签+真实 build | ✅ | code=0, step log 落库 |
| `GET /api/builds/{id}/stream` SSE | ✅ | curl -N 收 4 事件 (3 step + 1 build) |
| env vars 注入 drone | ✅ | log 看到 envVars=[JAVA_HOME, DB_PASSWORD] |
| `test-m5-2.ps1` 13/13 | ✅ | drone 集成 E2E |
| `test-m5-5.ps1` 8/8 | ✅ | env vars 注入 E2E |
| `pnpm typecheck` | ✅ | 0 errors |
| `pnpm build` | ✅ | 729ms, BuildDetail chunk 5.13 KB |
| `pnpm test` (vitest) | ✅ | 6/6 |
| Vite dev server (5179) | ✅ | HTML 200 OK |
| GitHub push | ✅ | 7 commit (M5 1 + M5 2.1-2.3 + M5 3 + M5 5 + M5 6) → origin/main |

### M5 踩坑 (留底 + 进 memory)

| # | 问题 | 修法 |
|---|---|---|
| 1 | build_record 表没 updated_at 字段 | 不继承 BaseEntity, 自己定义 id/createdAt/deleted |
| 2 | `@Async` 不支持 String 返回 | 用 Java 21 `Executors.newVirtualThreadPerTaskExecutor()` 显式提交 |
| 3 | `@Async` + JDK 动态代理类型不匹配 | 同上, 不依赖 Spring proxy |
| 4 | `@Transactional` 跟异步任务 commit 时序冲突 | 单 SQL 自动 commit, V1.5 业务复杂再加 |
| 5 | PowerShell `ConvertTo-Json` 输出跟 raw body 字节不一致 | HMAC 验签必须用 raw string |
| 6 | PowerShell 5.1 4xx 异常处理 | 业务码优先 (HTTP 永远 200) |
| 7 | Java 21 变量名 `log` 跟 SLF4J 冲突 | 改 `buildLog` / `stepLog` |
| 8 | EventSource 不走 Vite proxy | 直连 `sseBaseURL=http://localhost:8080` |

详细 memory 见 `C:\Users\Administrator\.minimax\agents\mavis\memory\MEMORY.md`。
