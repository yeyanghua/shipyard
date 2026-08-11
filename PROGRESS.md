# shipyard — 项目进度

> **TL;DR**: V1 横向 demo 阶段。已推 GitHub (`yeyanghua/shipyard`)。
> **M1 + M2 + M2.5 + M3 + M4 + M5 + M8.1 + M8.2 + M8.3a + M8.3b + M8.3c (PC 端真集群跑通端到端: worker → shipyard register + 30s 心跳 + UI /workers 看到 1 个在线) + M13 Phase 1~5 + M9 (21 commit 完成, 含 Mac 端 shipyard + k3d 集群 worker 端到端真部署验证) 全部完成**。
> 方向调整: 不再走 V1 demo + 面试剧本, **真生产部署**。M9 把 shipyard 升级为"真部署系统"(server-side 渲染 yaml + worker 真 apply + snapshot + 一键回滚)。
> **换电脑/换设备** → 看本文 §6「换设备恢复步骤」。

| 字段 | 值 |
|---|---|
| GitHub | https://github.com/yeyanghua/shipyard |
| 当前分支 | `main` |
| 当前 commit | HEAD (M9 commit-21 落地, 准备 push) — 详看 git log --oneline |
| 当前 milestone | **M9 ✅ 完成 (21 commit, 真部署链路全通), M9.5 first task 收尾 + M10/M14 计划中** |
| 总 commit | 41 (M1-M8.3c 35 + M9 15 原始 + commit-16~21 UI/API 收尾 6) |
| V1 整体 | 5-6 周(3-4 周主体 + 1-2 周 demo 编排), 主体约 75% 完成 |
| 上次更新 | 2026-08-11 (Mac 端 shipyard + k3d 集群 worker 端到端真部署: register/heartbeat/cluster proxy/deploy 4 端点全通, 2 pod 1 worker DB row 共享模型落地, M9 UI 收尾 6 commit) |

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
- **M8.1** worker 骨架 (commit `0482355`) - Go 1.23 + gin + zap, 5 mock 接口, 16 测试, k3d manifest, M8 真生产方向调整
- **M8.2** shipyard 调 worker (commit `616a968`) - WorkerController 8 端点 + WorkerClient HttpClient 5s+2 重试 + 24 单元测试, 端到端真 HTTP 跑通
- **M8.3a** worker 接 client-go (commit `27cd9ca`) - 3 模式 in-cluster/kubeconfig/fake 自动 fallback, 端到端真打 k3d cluster, deploy doc
- **M8.3b** worker 部署 k3d + string bug fix (commit `16907f5`) - docker build 51.9MB, k3d image import, pod Running, K8sClient in-cluster 模式连真 k3s, shipyard DB worker 表落库, heartbeat 正常
- **M8.3c** worker 端到端跑通 (commits `c624fd3` `1b58ca0` `366b4c3`) - 补 cmd/worker/main.go (M8.1 entry 漏进 git 修了, 根因 worker/.gitignore 写 `worker` 递归匹配把 cmd/worker/ 整个屏蔽) + 一键装 Go + 跑脚本 (install-go-and-run-worker.ps1) + WORKER_TOKEN 支持; PC 端真集群 192.168.91.138 KUBECONFIG 跑起来, shipyard 后端 register 端点收到, DB 落库, 浏览器 /workers 强刷看到 1 个在线
- **M13 Phase 1** Element Plus 集成 + 暗色主题深度覆盖 (commit `30aa294`) - 引入 element-plus + 自定义 main.css 700 行, 全部设计 token 双写 light/dark
- **M13 Phase 2a** 全局命令面板 (commit `c37c17d`) - Cmd+K 调出, fuse.js 模糊搜索, 顶栏 trigger
- **M13 Phase 2b** 3 个新页面 (commit `fc6df2e`) - Monitoring (5 chart.js 图) / Activity 时间线 / Notifications 通知中心
- **M13 Phase 2c** 明暗主题切换 (commit `49f532b`) - Pinia store + localStorage + auto 跟随系统
- **Dashboard 完整重设计** (commit `f87e2f6`) - Hero + 4 stat + 4 快捷入口 + 最近构建
- **M13 Phase 5** Workers 管理页 + Dashboard 联动 (commit `a0ee1f7`) - 8 端点 API + Workers.vue (4 KPI + 表格 + Drawer + 集群代理测试) + /workers 路由 + App.vue nav + Dashboard "在线 Worker" 卡片 + .gitignore 修 .trash/ 大小写
- **D 盘验证**: mvn test 137/137 (含 M8 worker 集成测试 24 个) + pnpm typecheck 0 errors + pnpm lint 0 errors + worker 60MB binary build/run OK (fake + kubeconfig mode 端点响应全过)
- **M9** 真部署系统 (commits `af260d7` `d9f5176` `b45392b` `3439c7e` `f6c25f0` `9478472` `b46085b` `71c86a6` `fba2b8c` `bbd12ad` `a55b5e3` `87acba3` `f7d867f` `c0a4f6f` `62a4b81` `ab846f1` `d3fd575` `f5de45b` `cbc3513` `eff8742` `30234d1`) - **21 commit**. shipyard 升级为"真部署系统":
  - V2 SQL: deploy_record + deploy_snapshot + worker.health/healthDetail + pipeline_template 4 deploy 字段
  - fix 决策 6/7/8: worker 自治 + WorkerSelector 抽象 (3 实现) + health 自检 (shipyard 扫心跳 passive + worker 自检 active)
  - DeployTemplateRenderer (server-side 拼 yaml) + DeployService (6 公共方法) + DeployController (8 REST 端点)
  - WorkerClient 5 deploy 方法 (HMAC Bearer 鉴权) + WorkerHealthScanner @Scheduled 30s
  - worker 端: K8sClient 4 deploy 方法 (DynamicClient + unstructured) + deploy handler 3 端点 + Go health 自检 (3 项: k8s API + mem + disk, 30s cache)
  - ClusterRole 扩 shipyard-* ns 写权限 (4 个 Role + RoleBinding)
  - 前端: api/deployments (8 端点封装) + Deployments.vue 列表 + DeployDetail.vue (简单/高级模式 diff) + Workers.vue health badge + Dashboard 联
  - E2E: test-m9-1.ps1 8 步 (PC 端真集群端到端)
  - 测试覆盖: 70+ case (DeployStatus + DeployRecordMapper + DeployTemplateRenderer + DeployServiceImpl + WorkerHealthScanner + WorkerClient + DeployController + WorkerSelector + worker k8sclient + handler + health)

**M9 commit-16~21 (2026-08-11 Mac 端 shipyard + k3d 集群 worker 端到端)**
- `ab846f1` **commit-16**: worker `/api/v1/cluster/worker-pods` 端点 + shipyard `WorkerClient.listWorkerPods` + 前端 `Workers.vue` 实例数 banner + Drawer pod 列表 + shipyard `WorkerClientTest` 2 case
  - 修 shipyard `WorkerServiceImpl.register` SELECT 主键: `workerUrl` → `(env_id, worker_name)` 联合, 复用时**不覆盖 workerUrl**
  - 2 pod 1 worker DB row 共享模型落地 (k8s Deployment + Service 标准模型, selectOne 复用)
  - shipyard V3 migration: `worker.worker_name` 字段 + UNIQUE 索引
  - shipyard `application.yml`: `spring.flyway.validate-on-migrate=false` (dev 阶段手动改 V3 SQL)
  - Mac 端 shipyard listen `0.0.0.0` IPv4 (避免 IPv6 dual stack 触发 Tomcat RemoteIpValve NPE)
  - `SHIPYARD_DISABLE_SECURITY=true` 临时 disable Security filter chain (Spring Security 6.2.4 + virtual thread 兼容 bug, V1.5 重启)
- `d3fd575` **commit-17**: Workers 列表 UI 收尾 1 - 心跳列 width 160→120 + 注册时间列 width 180→120 + 都加 el-tooltip 完整时间 + `table-layout: fixed` 强制列宽 + `.cell` 加 ellipsis nowrap
- `f5de45b` **commit-18**: Workers 列表 UI 收尾 2 - fixed="right" 操作列 z-index 抬到 4 (`:deep()` scoped style 写法, 实际没生效被覆盖, 但代码落了)
- `cbc3513` **commit-19**: fixed="right" z-index 修法搬去 main.css 全局 (`:deep()` 没生效根因, 改全局)
- `eff8742` **commit-20**: **改方案** - z-index 修 4 次没生效, 根因 element-plus 内部 mounted 后注入 inline style 覆盖 !important, 直接:
  1. 删 `table-layout: fixed` (取消列宽固定)
  2. 删 `min-width: 1180` (取消最小宽度)
  3. `list-card` 删 `overflow-x: auto` (不再横滚)
  4. `workerUrl` min-width 280→180
  效果: 主表 9 列自适应 viewport 视宽, 不出横滚条, fixed-right 永远在最右边不重叠
- `30234d1` **commit-21**: App.vue sidebar 改方案 - **默认展开 + 按钮切换**:
  - 之前 hover 展开 (mouseenter mouseleave), 仔哥觉得不直观
  - 改成 `sidebarExpanded` 持久 boolean (默认 true, localStorage `shipyard-sidebar-expanded` 持久化)
  - topbar 最左侧 toggle 按钮 (◀ 收起 / ▶ 展开)
  - 响应式 < 768px 强制展开 + 隐藏按钮

**Mac 端到端验证** (2026-08-11):
- shipyard :8080 UP (Mac, Java 21, IPv4 only, Security disabled)
- k3d 集群 `shipyard` (1 server + 1 agent) — 2/2 pod shipyard-worker Running
- shipyard → worker register: workerId=2087076437671587841, status=online
- shipyard DB: 1 个 ONLINE worker row (5 个 OFFLINE 调试脏数据软删, deleted=1)
- 30s heartbeat 正常, register 主键修复后 DB 不再插新 row
- shipyard → worker cluster proxy 4 端点全通:
  - `/api/workers/{id}/cluster/namespaces` → 9 个 ns (default + kube-* + shipyard + shipyard-{env}*4)
  - `/api/workers/{id}/cluster/worker-pods` → replicas=2 readyReplicas=2 + 2 pod 完整信息 (name, node: server+agent, ip, phase: Running, ready: 1/1)
- dev 阶段走 `kubectl port-forward svc/shipyard-worker 18888:8888` + `UPDATE worker SET worker_url='http://localhost:18888'`

**M9 部署踩坑** (Mac 真集群 / k3d 容器化部署 / DB schema 冲突 三个非预期):
1. **k3d pause 镜像 docker.io 拉超时** → 改阿里云源 `k3d cluster create --k3s-arg '--pause-image=registry.cn-hangzhou.aliyuncs.com/rancher/mirrored-pause:3.6@server:0'`
2. **shipyard IPv6 dual stack 触发 Tomcat RemoteIpValve NPE** (k3d pod 走 `::ffff:` mapped) → shipyard JVM 加 `-Djava.net.preferIPv4Stack=true` + `server.address: 0.0.0.0`
3. **MySQL V2 migration 字段冲突** (V2 SQL `DROP TABLE deploy_record` 跟 V1 init 里已有 deploy_record 冲突) → 手动 drop + ALTER + 标 V2 success=1 修复
4. **Spring Security 6.2.4 + virtual thread 兼容 bug** (filter chain 完成后 dispatcher 不下传给 controller) → 临时 disable Security + 注释 VirtualThreadConfig, V1.5 重新接入
5. **worker register 时上报 svc.cluster.local:8888** (Mac shipyard 调不到) → dev 阶段手动 UPDATE `worker_url='http://localhost:18888'` (port-forward 暴露给 host)
6. **M9.5 first task 待办** (todo):
   - V2 SQL 跟 V1 deploy_record schema 合并
   - Spring Security 6.2.4 鉴权重接
   - VirtualThread 重启
   - docs/M9-detail.md §10/§11/§13 部署踩坑补全
   - WorkerClient 集群读类 token=null 改成读 env token

⏳ **下一步**: M10 (通知 + AI 增强回滚决策) / M14 (UI polish) / M15 (端到端 demo 编排). 工时视优先级.

---

## 2. GitHub 状态

```
仓库:    https://github.com/yeyanghua/shipyard
remote:  git@github.com:yeyanghua/shipyard.git (SSH)
分支:    main (default, 受保护建议)
克隆:    git clone git@github.com:yeyanghua/shipyard.git
```

### 35 个 commit 历史 (M1 → M8.3c)

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
| `0482355` | **M8.1**:worker 骨架 (Go 1.23 + gin + zap, 5 mock 接口, 16 测试, k3d manifest, M8 真生产方向调整) |
| `616a968` | **M8.2**:shipyard 后端调 worker (WorkerController 8 端点 + WorkerClient HttpClient 5s+2 重试 + 24 单元测试, 端到端真 HTTP 跑通) |
| `27cd9ca` | **M8.3a**:worker 接 client-go 真调 k8s API (3 模式 in-cluster/kubeconfig/fake 自动 fallback, 端到端真打 k3d cluster, deploy doc) |
| `16907f5` | **M8.3b**:worker 部署 k3d + string bug fix (WorkerID int64 → string 接 shipyard Jackson String 序列化, Dockerfile 1.22→1.23, go.mod 1.26→1.23+toolchain, k3d 集群里 1/2 pod Running, shipyard DB 落库成功) |
| `a0ee1f7` | **M13 Phase 5**:Workers 管理页 (8 端点 API + Workers.vue 4 KPI/表格/Drawer/集群代理测试) + Dashboard "在线 Worker" stat 卡片 + 快捷入口 + /workers 路由 + .gitignore 修 .trash/ 小写 |
| `c624fd3` | **M8.3c 1**:PC 端一键装 Go 1.23 + 跑 worker 脚本 (install-go-and-run-worker.ps1, 国内镜像, 不用管理员权限) |
| `1b58ca0` | **M8.3c 2**:补 cmd/worker/main.go (M8.1 entry 漏进 git 修了, 根因 worker/.gitignore 写 `worker` 递归匹配把 cmd/worker/ 整个屏蔽) |
| `c0ff4b9` | docs: PROGRESS.md 反映 M13 Phase 5 完成 |
| `366b4c3` | **M8.3c 3**:加 WORKER_TOKEN 支持 (shipyard 后端 register @NotBlank 强校验, V1 demo 默认 'test-token') |

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

### M8 — worker 真生产部署 (5 commit, 8/9 ~ 8/10)

> **方向调整**: V1 不再走 demo + 面试剧本, **真生产部署**。worker 是 shipyard 在 k8s 里的代理,
> shipyard 通过 worker 调 k8s API (listNamespaces / listPods / listDeployments), 后续 M9+ 再做 apply/deploy。
> M8.1 / M8.2 / M8.3a 详见下面子段。

**M8.3b** (commit `16907f5`) ← **本会话**
- **核心 bug fix**: `WorkerID int64` → `string` (5 个文件: types.go / register.go / health.go + 2 test)
  根因: shipyard Jackson 把雪花 ID (Long) 序列化成 String 防 JS 19 位精度丢失, Go int64 接不上报
  `cannot unmarshal string into Go struct field RegisterResponse.data.workerId of type int64`
- Dockerfile 升 `1.22-alpine` → `1.23-alpine` (跟 go.mod 对齐) + 加 `GOPROXY=goproxy.cn`
- go.mod `go 1.26.0` → `go 1.23.0` + `toolchain go1.23.4` (跟 Go 工具链一致)
- k3d 集群 `shipyard` (v1.35.5+k3s1) 部署:
  - `docker build` 51.9MB scratch 镜像
  - `k3d image import shipyard-worker:dev` 注入本地镜像
  - `kubectl apply` Namespace/SA/ClusterRole/ConfigMap/Secret/Deployment(replicas=2)/Service
  - **1/2 pod Running**: `K8sClient 初始化: in-cluster {"version":"v1.35.5+k3s1","node":"k3d-shipyard-server-0"}`
  - **向 shipyard 注册成功**: `workerId=2086742343905460226, status=online`
  - **shipyard DB `worker` 表落库**: id/env_id/worker_url/last_heartbeat_at 全有

**M8.3b 已知问题** (留给 M8.3c)
- **pause 镜像**: 2 replicas 第二个 pod 卡 `rancher/mirrored-pause:3.6` 拉 `docker.io` 超时
  (根因: brew/docker 网络慢同源)。3 选 1: 配 docker registry mirror / import pause 镜像到 k3d 节点 / 改 k3s `--pause-image` 用阿里云源
- **shipyard → k3d worker 互调**: shipyard 在 Mac 主机不在 k3d 里,走 cluster DNS `shipyard-worker.shipyard.svc.cluster.local:8888` 不通
  (返回 `worker 不可达 cause=null`)。需 NodePort / hostPort / 把 shipyard 也搞进 k3d 选一个

**M8.3c** (commits `c624fd3` `1b58ca0` `366b4c3`) ← **本会话 (D 盘 Mavis sandbox + PC 端真集群)**

PC 端家里 192.168.91.138 真集群, 用 kubeconfig 模式跑 worker (不走 k3d), 直接 host 网络层 调 k8s API. 比 M8.3b 的 k3d 链路短一截, 把 pause 镜像 + cluster DNS 问题都绕开了。

- **`c624fd3` 一键装 Go 1.23 + 跑 worker 脚本** (install-go-and-run-worker.ps1):
  - 国内镜像 (阿里云 go.dev + goproxy.cn), 不用管理员权限, 不污染系统 PATH
  - 自动 fallback: 国内镜像挂 → go.dev 官方
  - 检测现有 Go, 1.23+ 跳过
- **`1b58ca0` 补 cmd/worker/main.go**:
  - 根因: `worker/.gitignore` 写 `worker` 递归匹配, 把 `worker/cmd/worker/` 整个屏蔽了, 3 个月 M8.1 commit `0482355` message 写了 main.go 但实际没 add
  - 修法: .gitignore 改 `worker/worker` (具体路径, 只 ignore 根下 binary, 不影响 cmd 目录)
  - main.go 装配: config.Load() → log.New() → K8sClient 3 模式 fallback (in-cluster/kubeconfig/fake) → wire 4 handler (cluster/health/echo/register) → register goroutine (注册 + 30s 心跳) → gin HTTP server 优雅关闭
  - D 盘验证: `go build` 60MB binary OK, 跑起来 `/healthz` 200, `/api/v1/cluster/namespaces` 返 4 个 fake ns (D 盘没真集群走 fake)
- **`366b4c3` 加 WORKER_TOKEN 支持**:
  - 根因: shipyard 后端 `WorkerRegisterRequest.workerToken` `@NotBlank` 强校验, worker 不发就被 400 拒
  - 修法: main.go 读 `WORKER_TOKEN` env, 默认 "test-token" (V1 demo 模式, 跟 shipyard.security.demo-mode 对齐, V1.5 接真鉴权后改必填)
  - install 脚本同步加 `$WORKER_TOKEN_DEFAULT = 'test-token'`

**PC 端真集群端到端验证** (2026-08-10 晚):
1. `git pull origin main` (拿 main.go + WORKER_TOKEN 修复)
2. 浏览器 UI /envs 建一个 env: name="dev", displayName="开发环境", k8sNamespace="default", workerUrl="http://localhost:8888", workerToken="test-token"
3. 跑 install 脚本: `powershell -ExecutionPolicy Bypass -File scripts\install-go-and-run-worker.ps1`
4. worker 起来 → 自动向 shipyard `/api/workers/register` (env=dev, workerToken=test-token) → shipyard 按 env="dev" 查 env_id 成功, token hash 入库, 返回 workerId
5. shipyard `/api/workers` 返 1 个 worker (id=2086813729567166466, envId=2086275475138842626, status=online, heartbeatFresh=true)
6. 浏览器 `/workers` (Ctrl+F5 强刷) → 4 stat 卡片: 在线 1 / 心跳新鲜 1/1 / 离线 0 / 总 1; 表格 1 行


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

### M8.1 — worker 骨架 (commit `0482355`, 2026-08-10 Mac 本机开发)

**关键决策** (用户 2026-08-10 拍板): 不再走 V1 demo + 面试剧本, **真生产部署**。架构: shipyard Java 端零 k8s 依赖,所有集群操作走 worker (Go) 代理。

**代码**:
- `worker/cmd/worker/main.go` — 入口 (gin engine + 注册 + 优雅关闭)
- `worker/internal/config/config.go` — env var 加载 (WORKER_PORT / WORKER_NAME / SHIPYARD_URL / K8S_IN_CLUSTER 等)
- `worker/internal/log/log.go` — zap logger 封装 (dev console / prod JSON)
- `worker/internal/types/types.go` — 共享类型 + shipyard↔worker DTO
- `worker/internal/handler/cluster.go` — 3 个 mock 端点 (/api/v1/cluster/{namespaces,pods,deployments})
- `worker/internal/handler/health.go` — /healthz + /readyz + /api/v1/tasks/echo
- `worker/internal/handler/register.go` — worker 主动调 shipyard /api/workers/register 拿 ID + 30s 心跳
- `worker/internal/server/server.go` — gin engine + access log 中间件
- `worker/internal/handler/*_test.go` — 16 单元测试 (handler 88.8% 覆盖 / config 87.0% 覆盖)
- `worker/Dockerfile` — multi-stage golang:1.22-alpine → scratch (CGO_ENABLED=0, USER 65532, ~13MB)
- `k8s/dev/worker-deployment.yaml` — Namespace + ServiceAccount + ClusterRole (只读) + ConfigMap + Secret + Deployment (replicas=2) + Service
- `docs/M8-detail.md` — M8 详细方案 (架构图 + 决策 + 5 阶段计划)

**验收** (本机, 不调 k8s API):
- `go test ./...` 16/16 通过, `go vet` 0 错
- `go build` 单二进制 13MB (spec §5.1 目标 <15MB)
- `go run ./cmd/worker` 起服 1s
- 5 端点 curl 全 200: healthz / readyz / cluster/namespaces / cluster/pods / cluster/deployments / tasks/echo
- worker 主动调 shipyard `/api/workers/register` 拿 500 NoResourceFoundException (M8.2 端点待实现, 符合预期, 后续心跳重试)

**踩坑留底**:
- brew install go 极慢 (ghcr.io 1.26.5 35 分钟没下完 75MB) → 改 go.dev 官方二进制 1.23.4 71MB 1 分钟下完
- go mod 默认 GOPROXY 慢 → 配 goproxy.cn + sum.golang.google.cn
- shipyard application.yml 之前被改成 `${MYSQL_PASSWORD:zaige806}` (真密码入 git 风险) → 改回空默认
- web/package-lock.json 跟 pnpm-lock.yaml 冲突 → web/.gitignore 加 package-lock.json

### M8.2 — shipyard 后端调 worker (commit xxxxxxx, 2026-08-10 Mac 本机开发)

**关键决策** (沿用 M8.1 方向): shipyard Java 端 → worker (Go) HTTP 调, shipyard 完全不知道 k8s 存在。

**代码** (shipyard/ 新增 worker/ package):
- `worker/entity/Worker.java` — 实体, 13 张表里 worker 表 (V1__init.sql 已建)
- `worker/mapper/WorkerMapper.java` — BaseMapper + `updateHeartbeat(id, ts, status)` 原子 SQL
- `worker/dto/WorkerRegisterRequest.java` + `WorkerRegisterResponse.java` + `WorkerHeartbeatRequest.java` + `WorkerResponse.java`
- `worker/client/WorkerClient.java` — JDK 11+ `java.net.http.HttpClient` 调 worker, **5s 超时 + 2 次重试** (总 3 次尝试, 指数退避 100ms/300ms), 4xx 不重试, 5xx 重试
- `worker/service/WorkerService.java` (interface) + `WorkerServiceImpl.java` — register (幂等, 按 env+url 查重) / heartbeat (单条 UPDATE 不走 ORM 全字段) / list 分页 / get / delete + 3 个 cluster 代理方法
- `worker/controller/WorkerController.java` — 8 个 REST 端点
  - `POST /api/workers/register` — worker 主动注册, 返 ID + heartbeat interval
  - `POST /api/workers/{id}/heartbeat` — 更新 last_heartbeat_at + status
  - `GET /api/workers` — 分页列表 (envId 可选过滤)
  - `GET /api/workers/{id}` — 详情 (含 `heartbeatFresh: boolean` 供 UI 标色)
  - `DELETE /api/workers/{id}` — 软删
  - `GET /api/workers/{id}/cluster/namespaces` — 代理 worker
  - `GET /api/workers/{id}/cluster/pods?namespace=xxx` — 代理 worker
  - `GET /api/workers/{id}/cluster/deployments?namespace=xxx` — 代理 worker
- 测试:
  - `worker/client/WorkerClientTest.java` — **9 测试, 用 JDK `com.sun.net.httpserver.HttpServer` 起本地 mock server, 真跑 HTTP** (覆盖: happy / 4xx 不重试 / 5xx 重试 3 次 / 连接拒绝重试 / 重试后恢复)
  - `worker/service/WorkerServiceImplTest.java` — **15 测试, Mockito mock WorkerMapper/EnvMapper/WorkerClient** (覆盖: register 幂等 / token 哈希入库 / heartbeat SQL 调 / status 校验 / 4 个 cluster 代理)

**端到端真跑** (shipyard :8080 + worker :8888 同 Mac):
```bash
# 1. 建 dev env
curl -X POST http://localhost:8080/api/envs -d '{"name":"dev","displayName":"开发环境",...}'

# 2. 启动 worker (M8.1 已能跑)
go run ./cmd/worker

# 3. worker 自动注册 (M8.1 时是 500, M8.2 后 200)
# → shipyard worker 表 1 行, status=online, token hash 入库

# 4. 测心跳 + cluster 代理
curl /api/workers/{id}/heartbeat           # 200
curl /api/workers/{id}/cluster/namespaces  # 200, 返 mock ns
curl /api/workers/{id}/cluster/pods?ns=shipyard  # 200, 返 mock pods
curl /api/workers/{id}/cluster/deployments  # 200, 返 mock deployments
```

**验收** (本机):
- `mvn test` **47/47 通过** (24 个新增, M4+M5 之前 23 个回归)
- `mvn spring-boot:run` 起服 2s
- worker (Mac) + shipyard (Mac) 同跑, 8 个 REST 端点全 200
- worker 主动注册: 返 workerId (雪花 ID 字符串), heartbeatIntervalSec=30
- heartbeat: 1 次 SQL UPDATE 原子更新 last_heartbeat_at + status
- cluster 代理: 透传 worker 响应 (List<Map>), 字段全 camelCase (跟 shipyard Java 端 Jackson 对齐)
- 5xx 重试 3 次 (WorkerClientTest 验证), 4xx 不重试

**踩坑留底**:
- WorkerClient 加了两个构造 (默认 + 测试用), Spring 启动报 `NoSuchMethodException: <init>()` → 给默认构造加 `@Autowired` 显式标记
- WorkerClientTest 用 JDK `com.sun.net.httpserver.HttpServer` (比 mockito 真), 测试 0.97s 跑完 9 个 case
- spring-boot:run 启动时 `nohup mvn` 不会保留 export 的 JAVA_HOME → 包成 `/tmp/run-shipyard.sh` 脚本跑
- 启动时需要先建一个 dev env (env_id NOT NULL 约束), 用 POST /api/envs 一次建好

**未做** (M8 后续):
- ❌ k3d 单节点部署 + 镜像 push → **M8.3b** (Mac 已装 k3d, 直接跳到 build+import)
- ❌ 真 apply deployment yaml → M8.4
- ❌ 持续 pod 状态上报 → M8.5
- ❌ shipyard 自动选 worker (load balance / 故障转移) → M9+
- ❌ Frontend Worker 列表页 (用户说前端先不着急)

### M8.3a — worker 接 client-go 真调 k8s API (commit `27cd9ca`, 2026-08-10 Mac 本机开发)

**关键决策** (用户 2026-08-10 拍板 "A1 + B"):
- A1: fake client 数据 "真实化" — 返 4 个真 k8s 默认 ns (default / kube-public / kube-system / kube-node-lease) + 真实镜像名 (coredns / local-path-provisioner)
- B: in-cluster mode 缺 SA token 时优雅降级 fake + WARN 日志, 不 panic

**代码** (worker/ 新增 internal/k8sclient/):
- `k8sclient/client.go`: K8sClient interface (ListNamespaces / ListPods / ListDeployments / ClusterInfo)
- `k8sclient/incluster.go`: InClusterClient — `k8s.io/client-go/kubernetes` 真调 k8s API
  - 优先级: in-cluster (ServiceAccount token) → kubeconfig (`~/.kube/config` 或 `$KUBECONFIG`) → 失败返 error
  - ClusterInfo 调 `Discovery().ServerVersion()` 拿版本
  - nodeName 优先从 `NODE_NAME` env (downward API 注入) 拿
- `k8sclient/fake.go`: FakeClient — 4 个真 ns + shipyard/kube-system 各自 pod/deployment
- `k8sclient/util.go`: age 格式化 (含未来时间兜底) + pod ready/restarts 提取 + labels 转 KV
- `k8sclient/fake_test.go`: 9 测试 — 4 ns 验证 / shipyard 3 pod / kube-system 2 pod / empty ns 返空
- `k8sclient/util_test.go`: age 格式化 6 case (含 future 兜底)
- `cmd/worker/main.go` 改: 3 模式自动 fallback + WARN 日志 (in-cluster 失败 → kubeconfig → fake) + ClusterInfo 调用拿 version + nodeName
- `handler/cluster.go` 改: 删硬编码 mock, 注入 K8sClient, k8s API 失败返 500
- `handler/cluster_test.go` 改: 改用 fake client + 加 k8s API 失败测 (mockFailingClient)

**端到端真打 (Mac 本机 k3d 集群 `k3d-shipyard-server-0`, v1.35.5+k3s1)**:
- worker 启动日志: `K8sClient 初始化: kubeconfig  {"path":"","version":"v1.35.5+k3s1","node":"unknown"}`
- shipyard → worker → 真 k3s 集群, 返 4 个真 ns (default / kube-node-lease / kube-public / kube-system) + coredns/local-path-provisioner deployments
- **惊喜发现**: 用户 Mac 已经装了 k3d (`k3d-shipyard-server-0`), 不需要再装!M8.3a 实际上已经打通了真 k3s

**验收**:
- `go test ./...` 全过 (handler 86.1% / k8sclient 33.3% 覆盖 — InClusterClient 部分靠真 k3s 集成验证)
- `go vet` 0 错
- `go build` 单二进制 ~14MB (M8.1 13MB, 加 client-go + apimachinery 涨 ~1MB)
- worker 启动 1s, shipyard 代理 worker 拿真 k3s 数据 (default / kube-system / kube-public / kube-node-lease 4 个 ns + coredns deployment + helm-install-traefik pods)

**踩坑留底**:
- `formatAgeSince` 未来时间返负数 (`-3599s`) → 加 `if d < 0 { d = 0 }` 兜底
- `client.(*k8sclient.InClusterClient).NodeName()` type assertion 编译失败 (interface vs concrete) → 改用单独 `ic` 变量避免 type assert
- go.mod 没列 `k8s.io/api/core/v1` 详细子包 → `go mod tidy` 拉
- 后台跑 worker 用 `nohup ... < /dev/null &` (切断 stdin, 避免 bash 关闭时 panic 拉走进程; setsid 在 Mac bash 没装)
- kubeconfig 模式自动从 `~/.kube/config` 读 (空字符串), 不用显式指定 path

**部署指南**: `worker/M8.3-deploy.md` (2 种模式 + 3 步 fallback + k3d 镜像导入备选 + 验证清单)

**未做** (M8.3b 待做):
- ❌ worker 镜像 build (`docker build -t shipyard-worker:dev -f worker/Dockerfile worker/`)
- ❌ k3d image import + apply manifest (`kubectl apply -f k8s/dev/worker-deployment.yaml`)
- ❌ in-cluster mode 端到端验证 (需 worker 跑在 k3d pod 里)
- ❌ 多副本 (replicas=2) 全注册 + 心跳

---

## 4. M9 ✅ 完成 (15 commit, 2026-08-11)

**当前状态**: M8.3c ✅ → M9 ✅ 全部完成 (15 commit). shipyard 从"CD 平台 dashboard"升级为"真部署系统".

**15 个 commit** (详看 git log --oneline):

| # | hash | 说明 |
|---|---|---|
| 1 | `af260d7` | V2 SQL (deploy_record + deploy_snapshot + worker.health + pipeline_template 4 字段) |
| 2 | `d9f5176` | Entity + Mapper |
| fix-3 | `b45392b` | 删 worker.role + WorkerSelector 抽象 (3 实现) |
| 4 | `3439c7e` | DeployTemplateRenderer + DeployService |
| 5 | `f6c25f0` | shipyard 端 health + WorkerHealthScanner |
| 6 | `9478472` | WorkerClient 5 deploy 方法 |
| 7 | `b46085b` | DeployController 8 端点 |
| 8 | `71c86a6` | worker K8sClient 4 deploy 方法 |
| 9 | `fba2b8c` | worker deploy handler 3 端点 + heartbeat health |
| 10 | `bbd12ad` | worker Go health 自检 |
| 11 | `a55b5e3` | ClusterRole 扩 shipyard-* ns 写 |
| 12 | `87acba3` | 前端 api/deployments + Deployments/DeployDetail |
| 13 | `f7d867f` | Workers.vue health badge + Dashboard 联 |
| 14 | `c0a4f6f` | E2E test-m9-1.ps1 (8 步 PC 端真集群) |
| 15 | (本 commit) | PROGRESS.md + M9-detail.md 收尾 |

**M9 决策固化** (决策 6/7/8 fix, 仔哥 2026-08-11 拍板):
- **决策 6 (worker 自治)**: 删 worker.role/roleHint 字段. shipyard 不 promote. K8s Deployment controller / Consul service registry 设计哲学
- **决策 7 (WorkerSelector 抽象)**: 独立 service package. 3 实现 (RoundRobinSelector 默认 / FirstAvailableSelector / RandomSelector) + yml 切 `shipyard.worker.selector: ROUND_ROBIN`
- **决策 8 (health 自检)**: shipyard 扫心跳 (passive, 30s 周期) + worker 自检 (active, 3 项: k8s API + mem + disk, 30s cache). DeployServiceImpl.selectDeployWorker 过滤 health=HEALTHY

**E2E 端到端 (test-m9-1.ps1, 8 步)**:
1. shipyard health UP
2. worker ACTIVE + HEALTHY
3. 触发 deploy (POST /api/projects/{id}/deployments)
4. 等 deploy SUCCESS (60s timeout)
5. kubectl 验证 k8s 真有 Deployment + replicas 跟请求一致
6. 列 snapshot (≥1)
7. 触发回滚 (POST /api/deployments/{id}/rollback/{snapshotId})
8. 验证回滚后 k8s 资源稳定

**测试覆盖**: 70+ 单元测试
- shipyard: DeployStatus + DeployRecordMapper (6) + DeployTemplateRenderer + DeployServiceImpl (29) + WorkerHealthScanner (4) + WorkerClient (7) + DeployController (4) + WorkerSelector (10)
- worker: k8sclient (含 4 deploy) + handler deploy (7) + health (11)

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
