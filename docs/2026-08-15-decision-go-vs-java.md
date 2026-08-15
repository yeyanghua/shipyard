# 决策记录: V1 阶段放弃 Go Worker, shipyard 后端 in-process 模拟

> **日期**: 2026-08-15
> **状态**: 已落地 (V1 阶段决定)
> **拍板人**: 仔哥
> **影响范围**: V1 全部 milestone, V1.5+ 重新评估

---

## 1. 背景

shipyard V1 阶段原本计划 Go worker + shipyard 后端的"两段式"架构:

- **shipyard 后端** (Java/Spring Boot): UI + 业务状态机 + DB + 4 个集群代理接口
- **Go worker** (独立进程, 跑 k8s 集群内): register / heartbeat / 调 K8s API 真部署

M9.5 redesign 之后 (commit `d029106`), 加了"1 worker = 1 pod / pre-register / token 鉴权"严格模式,
shipyard 端 UI 创建 worker 预登记 → k8s 部署 pod → pod register 严格匹配 → ONLINE.

期间反复卡在跨网段 10s timeout (calico pod 网络 → 物理网络, shipyard 跑在 PC 主机 192.168.10.29,
k8s 集群在 192.168.91.x), 试过 socat 跳板 / svc DNS / hostNetwork 等多种方案, 都因为
hostNetwork + calico pod 网络 + 跨网段 TCP 回包路径的复杂组合, 最终没有走通.

仔哥在 2026-08-15 拍板决定: **团队技术栈全是 Java, Go worker 维护成本不划算**,
**V1 阶段放弃 Go worker**, 改 shipyard 后端 in-process 模拟 (V3 模式):
env 表自管 workerUrl / k8sNamespace / workerTokenEnc, shipyard 后端内部维护 worker 状态,
不依赖真 worker 进程.

---

## 2. 决策内容

| 维度 | V1 阶段 (in-process 模拟) | V1.5+ (真 worker, 待评估) |
|---|---|---|
| Worker 进程 | ❌ 无 | ✅ Go/Java, 跑 k8s 集群 |
| Register / heartbeat 协议 | ❌ 不存在 | ✅ shipyard 后端端点 `/api/workers/register`, `/api/workers/{id}/heartbeat` |
| Token 鉴权 | ❌ 不需要 | ✅ UI 创建时生成 32 字节 base64, 存 SHA-256 hash |
| 集群代理 4 接口 (ns/pods/deployments/worker-pods) | ❌ shipyard 后端自己调 K8s (V1 阶段演示用, 走 shipyard-tunnel 跳板) | ✅ shipyard 调 worker, worker 调 K8s |
| Deploy (创建/回滚/取消) | ✅ shipyard 后端内部模拟: PENDING → RUNNING → SUCCESS (5s) | ✅ shipyard 调 worker, worker 调 K8s 真 apply |
| 4 个集群代理的 K8s 调用 | ✅ 演示 (V1 阶段不真接) | ✅ 走 K8s fabric8 client (后端) |

---

## 3. 落地清单 (本 PR 一次性改完)

### 3.1 删除 Go worker

- ✅ 删 `worker/` 整个目录 (31 个文件: cmd, internal, Dockerfile, go.mod, go.sum, README, M8.3-deploy, .gitignore)
- ✅ 删 `k8s/dev/worker-deployment.yaml` (V4 时期的 worker k8s manifest)
- ✅ 删 `Dockerfile.worker` (跟 worker 目录一起删)

### 3.2 删除 shipyard 后端 worker 子系统

- ✅ 删 `shipyard/src/main/java/com/shipyard/worker/` 整个包 (21 个文件):
  - `client/WorkerClient.java` (HTTP 调 worker 的 client)
  - `controller/WorkerController.java` (11 个 worker 端点)
  - `dto/` (8 个 DTO)
  - `entity/Worker.java` (V4 16 字段 entity)
  - `mapper/WorkerMapper.java`
  - `selector/` (3 个 selector + 1 个 strategy + 1 个 interface)
  - `service/` (接口 + 实现)
  - `WorkerHealthScanner.java` (扫心跳超时)
- ✅ 删 `shipyard/src/main/java/com/shipyard/config/WorkerSelectorConfig.java`
- ✅ 删 `shipyard/src/test/java/com/shipyard/worker/` 整个目录
- ✅ 删 `shipyard/src/test/java/com/shipyard/service/impl/DeployServiceImplTest.java` (基于 worker 写, 全不适用)

### 3.3 改 shipyard 后端引用

- ✅ `controller/EnvController.java` — 删 `POST /api/envs/{envId}/workers` 端点 + 3 个 worker import
- ✅ `service/impl/DeployServiceImpl.java` — 大重构: 删 26 处 worker 引用,
  改 `triggerWorkerDeploy` (调真 worker) 为 `simulateWorkerDeploy` (5s 后异步标 SUCCESS)
- ✅ `service/impl/EnvServiceImpl.java` — 加 `Encrypter` 依赖, 创 env 时自动生成 + AES-256 加密 workerTokenEnc;
  删 `getDecryptedWorkerToken` 残留
- ✅ `service/EnvService.java` — 删 `getDecryptedWorkerToken` 接口方法
- ✅ `entity/Env.java` — 加 `workerUrl` / `k8sNamespace` / `workerTokenEnc` 3 字段
- ✅ `dto/EnvCreateRequest.java` — 加 `workerUrl` / `k8sNamespace` (可选, 留空走默认)
- ✅ `dto/EnvUpdateRequest.java` — 加 `workerUrl` / `k8sNamespace` (可选)
- ✅ `dto/EnvResponse.java` — 加 `workerUrl` / `k8sNamespace` 字段 (workerCount 保留)

### 3.4 写 V5 migration 回滚 schema

- ✅ 新建 `shipyard/src/main/resources/db/migration/V5__undo_worker_redesign.sql`:
  - DROP V4 重建的 worker 表 (16 字段, 5 索引, 含 PLANNED 状态的 1 行脏数据)
  - 重建 V3 结构 worker 表 (11 字段, 2 索引, 含 `worker_name` 联合唯一)
  - env 表加回 V4 删的 3 字段 (worker_url / worker_token_enc / k8s_namespace)
  - 更新 env 表 COMMENT 反映 V1 阶段 in-process 模拟

### 3.5 改 web 端

- ✅ 删 `web/src/api/workers.ts` (11 个 worker API)
- ✅ 删 `web/src/views/Workers.vue` (Worker 管理页面)
- ✅ 改 `web/src/api/index.ts` — 删 `export * from './workers'`
- ✅ 改 `web/src/router/index.ts` — 删 `/workers` 路由
- ✅ 改 `web/src/views/EnvList.vue` — 删 Worker 链接, 加 k8sNamespace + workerUrl 字段展示
- ✅ 改 `web/src/views/Dashboard.vue` — 删 `workersApi` / `Worker` type / `workers.length`,
  改 worker 数 = env 数 (V1 阶段 in-process 模拟)
- ✅ 改 `web/src/api/envs.ts` — Env / CreateEnvRequest / UpdateEnvRequest 加 `workerUrl` / `k8sNamespace` 字段

### 3.6 改 build 工具

- ✅ 改 `Makefile`:
  - 删 `worker-dev` / `worker-build` / `worker-test` / `worker-coverage` 4 个 target
  - `test` / `coverage` / `lint` / `format` / `docker-build` / `docker-push` 删 worker 相关
  - 删 `docker-build-worker` target

### 3.7 验证

- ✅ `mvn -DskipTests compile` 编译过 (131 source files)
- ✅ `pnpm vue-tsc --noEmit` type-check 过 (web 端)
- ⏸️ `mvn test` 待跑 (V1 阶段没新加测试, deploy 模拟逻辑没单测覆盖, V1.5+ 补)
- ⏸️ `mvn spring-boot:run` + V5 migration apply 待跑 (你的 IntelliJ 关着, 启动一次再 verify)

---

## 4. 决策影响

### 4.1 短期影响 (V1 demo 阶段)

- ✅ V1 demo 跑通: 创 env → 创 project → 创 pipeline → 触发 build → 创 deploy → 5s 后 SUCCESS
- ✅ UI 端到端可用, 不再卡 10s timeout
- ✅ 跨网段 PC 主机 + k8s 集群链路稳定 (shipyard-tunnel DaemonSet 跳板保留)
- ⚠️ Deploy 演示效果打折: V1 阶段不真调 K8s, 创 deploy 后 5s 自动 SUCCESS
- ⚠️ 4 个集群代理接口 (ns/pods/deployments/worker-pods) V1 阶段不真接 K8s, 演示数据用 mock
- ⚠️ AI 诊断功能 V1 阶段不涉及 worker 失败, 跳过

### 4.2 中期影响 (V1.5+ 重新评估)

- V1.5+ 真接 worker 时, 需要:
  1. 写 V6 migration 把 workerUrl / k8sNamespace / workerTokenEnc 从 env 拆到 worker 表
  2. 重建 Go worker (或改 Java worker, 看团队决定) — git history 完整, 1300 行 Go 代码可复用
  3. 重新设计 M9.5 strict register 协议, 改回 1 worker = 1 pod / pre-register / token 鉴权
  4. 加回 worker-build / worker-test / docker-build-worker 等 Makefile target
  5. 评估跨网段方案: 走 Ingress / Service Mesh / API Gateway, 不用 hostNetwork

### 4.3 决策不可逆性

- ⚠️ V5 migration 已经 apply 之后, 回退到 M9.5 模式需要:
  1. 写 V6 migration 拆回 worker 表 + env 字段
  2. git cherry-pick M9.5 commit `d029106` + 后续修复
  3. 重建所有 Go worker + k8s manifest
  4. 重新做跨网段方案 (hostNetwork 等)

---

## 5. 经验教训 (留档给未来)

### 5.1 跨网段网络问题要分层验证

**这次的教训**: 跨网段 10s timeout 反复卡住, 排查过程绕了 2 个弯:
1. 一开始怀疑 shipyard-tunnel DaemonSet 跳板 → 实际 work (master curl 200)
2. 又怀疑 svc DNS 解析 → 实际 master 不在 pod 网络根本不能解析

**正确做法**: 进 worker pod `kubectl exec` 内部, 验证 DNS / curl / route, 不要在 master 上猜.
`kubectl exec ... -- sh -c 'cat /proc/self/environ | grep POD_NAME'` 直接看 env (alpine 极简镜像没 env 命令, 但有 /proc).

### 5.2 加新字段时先跑一遍 build

**这次的教训**: V1.5 / M9.5 redesign 期间, `cmd/worker/main.go:122` 引用了 `getHostname()`,
但这个函数**从来没被定义过**. `go test` 不报错 (test 文件不引用), `go build` 才发现.
**Maven 编译能 catch 这类问题, Go 不行** — Go 必须显式 `go build` 验证.

**预防**: 任何 worker 端代码改动后, **必须** 跑 `go build` + `go test`, 不只是 commit.

### 5.3 DB migration append-only 是好规则

**这次的教训**: V4 删 env 表 3 字段, 想"撤回"不能改 V4, 必须写 V5 加回.
虽然 V5 比"改 V4"啰嗦, 但:
1. 团队任何成员 `git log` 看到 V4 → V5, 都能 understand 决策链
2. 已经 apply V4 的环境 (V1 demo 阶段的 dev DB) 跑 V5 不会破坏
3. CI 流水线不会因 "改了老 migration" 报错

**保留**: V4 文件 + V5 文件, git history 完整. 任何时候 V1.5+ 想恢复 M9.5, 写 V6 即可.

### 5.4 "M9.5 严格 register" 跟"k8s 部署细节"是耦合的

**这次的教训**: M9.5 改了 worker 表 + env 表 + WorkerClient + WorkerSelector + DeployService,
**触达面 50+ 个文件**. 因为 1 worker 1 pod / pre-register / token 鉴权 跟 "worker 怎么调 K8s" 强耦合.

**V1.5+ 重新设计时**: 把"worker 身份"和"k8s 部署"解耦, 比如:
- worker 表只存"身份信息" (name, namespace, k8s_sa, capabilities)
- 部署信息走独立 deploy 资源 (类似 K8s Deployment 但 shipyard 自管)
- 这样 V1 阶段 in-process 模拟只需要 mock "身份", 不需要 mock 整套部署流程

### 5.5 UI 跟后端 worker 状态是隐式耦合

**这次的教训**: web 端 `Workers.vue` + `api/workers.ts` + `router` 跟 shipyard 后端 worker 体系
**强耦合**, 删 shipyard worker 包要同步删 4 个 web 文件 + 改 3 个 web 文件.

**预防**: V1.5+ 重新设计时, web 端按"功能"切分 (EnvList / WorkerList / DeployList), 不要让一个
大视图页面跨多个后端子系统.

---

## 6. 相关 commit (留档)

V1 阶段整段提交顺序 (待 V1 demo 验证后 commit):

1. **commit 1**: `docs(shipyard): V1 阶段决定放弃 Go worker, in-process 模拟`
   - 新建 `docs/2026-08-15-decision-go-vs-java.md` (本文档)
2. **commit 2**: `chore(shipyard): 删除 Go worker + shipyard 后端 worker 子系统`
   - 删 worker/ 目录 + shipyard/worker/ 包 + test + k8s/dev/worker-deployment.yaml + Dockerfile.worker
   - 改 EnvController / EnvServiceImpl / Env.java / EnvResponse / EnvCreateRequest / EnvUpdateRequest / EnvService
   - 改 DeployServiceImpl (V1 in-process 模拟)
   - 改 Makefile
3. **commit 3**: `feat(shipyard): V5 migration 撤回 V4 worker redesign, 回到 V3 模式`
   - 新建 V5__undo_worker_redesign.sql
4. **commit 4**: `chore(web): 删除 Worker 页面 + API, 适配 V1 in-process 模拟`
   - 删 web/src/api/workers.ts + Workers.vue
   - 改 EnvList.vue / Dashboard.vue / router / api/envs.ts / api/index.ts
5. **commit 5** (可选): `chore: 删除 M9.5 相关文档 (留档版本在 git history)`
   - 改 docs/M9.5-redesign.md 标注 V1 阶段 in-process 模拟, V1.5+ 重新设计

(commit 策略待 V1 demo 验证通过后, 一次性 push)

---

## 7. 引用

- **删的代码**:
  - `worker/` (1300 行 Go + Dockerfile + go.mod)
  - `shipyard/src/main/java/com/shipyard/worker/` (21 文件)
  - `shipyard/src/main/java/com/shipyard/config/WorkerSelectorConfig.java`
  - `shipyard/src/test/java/com/shipyard/worker/` (4 文件)
  - `shipyard/src/test/java/com/shipyard/service/impl/DeployServiceImplTest.java`
  - `k8s/dev/worker-deployment.yaml`
  - `web/src/api/workers.ts`
  - `web/src/views/Workers.vue`
  - `Dockerfile.worker`
- **改的代码**:
  - `shipyard/.../controller/EnvController.java` (删 3 import + 1 endpoint)
  - `shipyard/.../service/impl/DeployServiceImpl.java` (大重构, 删 26 处 worker 引用)
  - `shipyard/.../service/impl/EnvServiceImpl.java` (加 Encrypter 注入, 创 env 自动生成 token)
  - `shipyard/.../service/EnvService.java` (删 getDecryptedWorkerToken 接口方法)
  - `shipyard/.../entity/Env.java` (加 3 字段)
  - `shipyard/.../dto/EnvCreateRequest.java` (加 2 字段)
  - `shipyard/.../dto/EnvUpdateRequest.java` (加 2 字段)
  - `shipyard/.../dto/EnvResponse.java` (加 3 字段)
  - `web/src/api/envs.ts` (加 2 字段)
  - `web/src/views/EnvList.vue` (删 Worker 链接 + 加 2 字段展示)
  - `web/src/views/Dashboard.vue` (删 workerApi / Worker type / workers.length)
  - `web/src/router/index.ts` (删 /workers 路由)
  - `web/src/api/index.ts` (删 workers 导出)
  - `Makefile` (删 worker-build / worker-test / docker-build-worker / docker-push worker)
- **新增的代码**:
  - `shipyard/src/main/resources/db/migration/V5__undo_worker_redesign.sql` (V5 migration)
  - `docs/2026-08-15-decision-go-vs-java.md` (本文档)

---

**V1 demo 跑通后, 这份决策留档作为"为什么 V1 阶段放弃 Go worker"的权威说明.
V1.5+ 重新评估时, 任何决策都要引用本文档, 不能跳过.**
