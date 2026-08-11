# M9 — shipyard snapshot + 回滚 (worker apply 集成)

> **作者**: Mavis (Mavis Code) · **日期**: 2026-08-11 · **状态**: 实施中
> 关联: M8.3c ✅ 完成(commit `366b4c3`),M9 把 shipyard 跟 worker 集成的 read-only 阶段(读 ns/pods/deployments)推到 apply 阶段
> 上游: PROGRESS.md §4 下一步 M9 计划

---

## 0. TL;DR

把 shipyard 从"CD 平台 dashboard"升级为"真部署系统" — 跟 build_record 平级新增 **deploy_record** + **deploy_snapshot** 实体,
shipyard 后端走 worker (`client-go` DynamicClient) 调 k8s API 真 apply 资源,snapshot 落库支持一键回滚。
1 环境支持 1 primary + 1 standby worker 高可用(self-elect + 故障自动转移)。

```
[shipyard web]  --POST /api/projects/{id}/deployments-->  [shipyard 后端]
                                                          │
                                                          ├─ 1. 渲染 yaml (DeployTemplateRenderer, 简单模式)
                                                          ├─ 2. 选 worker (env_id 过滤 + role=PRIMARY + status=online)
                                                          ├─ 3. 写 deploy_record (PENDING) + deploy_snapshot (渲染完 yaml + sha256)
                                                          ├─ 4. 调 worker POST /api/v1/tasks/deploy (HMAC + body 含 yaml)
                                                          │
[shipyard 后端]                                            ▼
                <--200 / 500--                       [worker Go (in k8s pod)]
                                                          │
                                                          ├─ DynamicClient unstructured → apply 资源
                                                          └─ 返 shipyard {phase, message, manifest}
```

---

## 1. 决策固化(13 条)

| # | 决策点 | 选择 | 影响 |
|---|---|---|---|
| 1 | deploy 跟 build 关系 | **独立链路**, 不穿 build | 各自状态机,中间靠 `image_tag` 衔接 |
| 2 | yaml 来源 | **shipyard server-side 渲染**(pipeline_template + image_tag + env vars) | 用户填业务字段,平台管 K8s 编排细节 |
| 3 | 高级模式 | **预览 + diff 只读** (用户改的能力 V1.5) | 防错 + 减少 shipyard 验证责任 |
| 4 | snapshot 抓取 | **只存 shipyard 渲染完的 yaml + sha256** | 简版,live_manifest 留 V1.5 |
| 5 | 部署 namespace | **`shipyard-{env_name}` 一对一** | 天然 RBAC 隔离,ClusterRole resourceNames 限定 |
| 6 | 多 worker 模式 | **1 primary + N standby**(N≥0,推荐 1) | primary 跑 deploy,standby 备机 + 故障接管 |
| 7 | worker 角色判定 | **启动 self-elect** | shipyard 看同 env 已有 online 数,0→PRIMARY / ≥1→STANDBY |
| 8 | 故障转移 | **shipyard `@Scheduled` 扫心跳**(90s 阈值) | primary unhealthy → 升级同 env 第一个 STANDBY |
| 9 | 渲染器实现 | **Go `text/template`** (跟 M5 BuildLog 渲染对齐) | 模板 shipyard 端 hardcode,用户填 struct |
| 10 | worker apply 工具 | **client-go `DynamicClient` + `unstructured`** | 不写死 schema,任意 K8s 资源可 apply |
| 11 | 回滚机制 | **worker 存历史 manifest list** + shipyard 选 snapshot 重 apply | 不依赖 `kubectl rollout undo`,自己管 |
| 12 | K8s 写权限 | **ClusterRole 扩 `shipyard-*` ns 写** + `ResourceNames` 限定 | 严格,不污染其他 ns |
| 13 | 工时 | **3-4 天**(2 天 shipyard 后端 + 1 天 worker Go + 1 天前端/E2E) | 比 PROGRESS.md 估的 1-2 天多,deploy 链路是 M9 大头 |

---

## 2. 数据模型

### 2.1 新增表(V2__add_deploy_tables.sql)

```sql
-- ============================================================
-- 1. deploy_record — 一次部署任务
-- ============================================================
CREATE TABLE `deploy_record` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `project_id`        BIGINT       NOT NULL COMMENT '关联 project.id',
  `env_id`            BIGINT       NOT NULL COMMENT '关联 env.id (决定 deploy 到哪个集群)',
  `build_record_id`   BIGINT       NULL     COMMENT '关联 build_record.id (镜像来源, 可空表示手动选 image)',
  `image_tag`         VARCHAR(255) NOT NULL COMMENT '实际部署的镜像 (例 nginx:1.27.0)',
  `namespace`         VARCHAR(64)  NOT NULL COMMENT '实际 ns (shipyard-{env_name})',
  `deploy_yaml_sha256` CHAR(64)    NOT NULL COMMENT '渲染后 yaml sha256, 快查 diff',
  `current_snapshot_id` BIGINT     NULL     COMMENT '当前生效 snapshot, deploy_record 1 — N snapshot',
  `status`            VARCHAR(16)  NOT NULL COMMENT 'PENDING / RUNNING / SUCCESS / FAILED / TIMEOUT / CANCELED',
  `error_message`     TEXT         NULL,
  `started_at`        DATETIME     NULL,
  `finished_at`       DATETIME     NULL,
  `triggered_by`      VARCHAR(64)  NOT NULL DEFAULT 'unknown',
  `trigger_type`      VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / GIT_PUSH (V1.5)',
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`           TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_project_env` (`project_id`, `env_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次部署任务';

-- ============================================================
-- 2. deploy_snapshot — 一次部署成功后的 yaml 快照
-- ============================================================
CREATE TABLE `deploy_snapshot` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `deploy_record_id` BIGINT      NOT NULL COMMENT '关联 deploy_record.id',
  `env_id`          BIGINT       NOT NULL,
  `project_id`      BIGINT       NOT NULL,
  `deploy_yaml`     LONGTEXT     NOT NULL COMMENT 'shipyard 渲染完的 K8s yaml (用户提交字段 + shipyard 补默认)',
  `deploy_yaml_sha256` CHAR(64)  NOT NULL,
  `created_by`      VARCHAR(64)  NOT NULL,
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_deploy_record` (`deploy_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部署 yaml 快照(回滚源)';
```

### 2.2 修改表

```sql
-- worker 表加 role 字段
ALTER TABLE `worker` ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'PRIMARY'
  COMMENT 'PRIMARY (跑 deploy) / STANDBY (备机, 故障接管)' AFTER `version`;

-- pipeline_template 表加 deploy 字段
ALTER TABLE `pipeline_template` ADD COLUMN `container_port` INT NULL
  COMMENT '主容器监听端口 (deploy 用)' AFTER `template_yaml`;
ALTER TABLE `pipeline_template` ADD COLUMN `replicas` INT NOT NULL DEFAULT 1
  COMMENT '副本数' AFTER `container_port`;
ALTER TABLE `pipeline_template` ADD COLUMN `namespace_pattern` VARCHAR(64) NOT NULL DEFAULT 'shipyard-{env_name}'
  COMMENT '目标 ns 模板' AFTER `replicas`;
```

### 2.3 实体关系

```
project 1 — N deploy_record
env     1 — N deploy_record
env     1 — N worker (1 PRIMARY + 0..N STANDBY, 推荐 1+1)
deploy_record 1 — N deploy_snapshot
deploy_record 1 — 1 current_snapshot_id (外键到 deploy_snapshot.id)
build_record 1 — N deploy_record (一个 build 可以 deploy 多次, 不同 env)
```

---

## 3. Shipyard 后端

### 3.1 DeployStateMachine

```
PENDING ──trigger──> RUNNING ──worker 返 200──> SUCCESS
                       │
                       ├──worker 返 4xx/5xx──> FAILED
                       │
                       └──30 分钟超时──────> TIMEOUT

PENDING / RUNNING 可被取消 → CANCELED
```

跟 BuildStatus 几乎一致,沿用 `isTerminal()` 模式(`SUCCESS / FAILED / TIMEOUT / CANCELED`)。

### 3.2 DeployTemplateRenderer (核心组件)

**输入**:`PipelineTemplate` + `imageTag` + env vars(已 resolve 过的)+ `envName`
**输出**:`String deployYaml` (K8s Deployment + Service + ConfigMap 三件套)

模板 hardcode 在 shipyard 代码里(Go `text/template` 渲染),用户填 struct 字段:

```go
type DeploySpec struct {
    Name          string            // 资源名(例 project.env, 如 myapp-dev)
    Image         string            // 镜像 tag
    Replicas      int               // 副本数
    ContainerPort int               // 主容器端口
    EnvVars       map[string]string // 注入到 Pod env (K8s envFrom configMap/secret, 简化版先 plain env)
    Labels        map[string]string // 业务 label
}
```

模板示例(简化版 V1):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{.Name}}
  namespace: {{.Namespace}}
  labels:
    {{- range $k, $v := .Labels }}
    {{$k}}: {{$v}}
    {{- end }}
spec:
  replicas: {{.Replicas}}
  selector:
    matchLabels:
      app: {{.Name}}
  template:
    metadata:
      labels:
        app: {{.Name}}
    spec:
      containers:
        - name: app
          image: {{.Image}}
          ports:
            - containerPort: {{.ContainerPort}}
          env:
            {{- range $k, $v := .EnvVars }}
            - name: {{$k}}
              value: {{$v}}
            {{- end }}
---
apiVersion: v1
kind: Service
metadata:
  name: {{.Name}}
  namespace: {{.Namespace}}
spec:
  selector:
    app: {{.Name}}
  ports:
    - port: 80
      targetPort: {{.ContainerPort}}
      protocol: TCP
  type: ClusterIP
```

**3 渲染模式**(M9 简版只做 1):
- **简单模式** (V1 默认): 走模板渲染
- **高级模式** (V1.5): 让用户直接填 yaml,shipyard 不解析
- **直接 yaml 模式** (V1.5+): 完全 skip 渲染,直接把 yaml 字符串给 worker

M9 只实现简单模式 + 高级模式"预览 + diff 只读"(用户改的开关不打)。

### 3.3 Worker 选择策略

```go
// M9 简版: 按 env_id + role=PRIMARY + status=online 取 1 个
// ORDER BY last_heartbeat_at DESC LIMIT 1
func selectDeployWorker(envId int64) (*Worker, error) {
    // 1. 查 worker 表: env_id=?, role=PRIMARY, status=online, deleted=0
    // 2. ORDER BY last_heartbeat_at DESC LIMIT 1
    // 3. 找不到 → 抛 BusinessException("该环境没有 PRIMARY worker 在线, 无法部署")
}
```

**M9.5+ 改进**:round-robin + 健康检查 + 故障转移 (按 spec §5.1)。

### 3.4 REST 端点(7 个)

| Method | Path | 用途 | 鉴权 |
|---|---|---|---|
| POST | `/api/projects/{id}/deployments` | 触发部署(buildRecordId OR imageTag + envId 必填) | yes |
| GET | `/api/deployments/{id}` | 查部署详情(含 current_snapshot) | yes |
| GET | `/api/deployments?projectId=&envId=&page=&size=` | 列表(分页) | yes |
| GET | `/api/deployments/{id}/snapshots` | 列历史 snapshot(回滚用) | yes |
| POST | `/api/deployments/{id}/rollback/{snapshotId}` | 一键回滚(把 snapshot 的 yaml 重 apply) | yes |
| POST | `/api/deployments/{id}/cancel` | 取消(PENDING/RUNNING 才能) | yes |
| GET | `/api/deployments/{id}/live-manifest` | 调 worker 拿 k8s 真生效的 manifest(高级模式 diff 用) | yes |

### 3.5 DeployService.createDeploy 流程

```
1. 校验 projectId / envId 存在
2. 校验 buildRecordId 合法(可选;不传时 imageTag 必填)
3. 解析 imageTag
   - 有 buildRecordId: 拿 build_record.image_tag
   - 无: 用 req.imageTag
4. 查 env → namespace = "shipyard-{env_name}"
5. 查 pipeline_template (active, projectId) 拿 containerPort / replicas / namespacePattern
6. 渲染 yaml (DeployTemplateRenderer)
7. 算 sha256
8. 选 worker (envId + role=PRIMARY + status=online, 1 个)
9. 写 deploy_record (PENDING)
10. 写 deploy_snapshot (deployYaml + sha256)
11. 异步调 worker /api/v1/tasks/deploy
12. 返 deploy_record (status=PENDING) 给前端
```

### 3.6 WorkerClient 5 新方法

```java
// 跟 shipyard 后端 WorkerClient 配套
Map<String, Object> deploy(String workerUrl, DeployRequest req);
Map<String, Object> rollback(String workerUrl, RollbackRequest req);
Map<String, Object> scale(String workerUrl, ScaleRequest req);
Map<String, Object> stop(String workerUrl, StopRequest req);
Map<String, Object> getManifest(String workerUrl, GetManifestRequest req);
```

body 全是 JSON,鉴权走 HMAC Bearer token(跟 M5 drone 同一机制,复用)。

---

## 4. Worker Go 端

### 4.1 K8sClient 新接口

```go
type K8sClient interface {
    // M8.3 已实现
    ClusterInfo(ctx) (version, node, err)
    ListNamespaces(ctx) ([]Namespace, err)
    ListPods(ctx, namespace) ([]Pod, err)
    ListDeployments(ctx, namespace) ([]Deployment, err)
    
    // M9 新增
    Apply(ctx, namespace, manifest string) (phase, message, err)  // 接收 yaml 字符串, 用 DynamicClient + unstructured
    Rollback(ctx, namespace, resource, name string) (phase, message, err)  // 拿旧 revision 重 apply
    Scale(ctx, namespace, resource, name string, replicas int) (phase, message, err)
    GetManifest(ctx, namespace, resource, name string) (manifest string, err)
}
```

### 4.2 实现细节

- **Apply**: 用 `client-go` 的 `DynamicClient` + `unstructured.Unstructured` 解析 yaml(`k8s.io/apimachinery/pkg/util/yaml.NewYAMLOrJSONDecoder`)
  - 资源 kind 从 yaml 拿(Deployment / Service / ConfigMap)
  - 拿 `gvr` (group/version/resource) from yaml.apiVersion + kind,或 hardcode 常见 5 种
  - 调 `dynamic.Resource(gvr).Namespace(ns).Create(ctx, obj, metav1.CreateOptions{})`(不存在)/ `Update(ctx, obj, metav1.UpdateOptions{})`(已存在)
- **Rollback**: shipyard 重发一个旧 snapshot 的 yaml,worker 不用做"撤销" — 走 Apply 一遍就行
- **Scale**: 走 Apply 但改 `spec.replicas`,或用 `apps/v1` typed client `Scale` API
- **GetManifest**: `dynamic.Resource(gvr).Namespace(ns).Get(ctx, name, metav1.GetOptions{})`,marshal 回 yaml 字符串

### 4.3 handler/deploy.go (3 端点)

```go
POST /api/v1/tasks/deploy
  body: { "namespace": "...", "yaml": "...", "name": "..." }
  调 K8sClient.Apply
  
POST /api/v1/tasks/rollback
  body: { "namespace": "...", "yaml": "..." }  // shipyard 重发旧 snapshot
  调 K8sClient.Apply(旧 yaml)
  
POST /api/v1/tasks/scale
  body: { "namespace": "...", "kind": "Deployment", "name": "...", "replicas": N }
  调 K8sClient.Scale
```

### 4.4 worker role self-elect 启动

main.go 启动时调 shipyard `/api/workers/register` 走新流程,带 `role_hint` (worker 自己想当的角色,默认 PRIMARY):

```
worker 启动:
  role_hint=PRIMARY
  ↓
  shipyard 看同 env 已有 online 数:
    0 → 给新 worker PRIMARY
    1+ → 给新 worker STANDBY
  ↓
  shipyard 写库, 返 worker_id + role
  ↓
  worker log "I am now PRIMARY/STANDBY"
  ↓
  PRIMARY 跑 deploy 任务, STANDBY 备机(只 heartbeat + 故障时接管)
```

**env 配**:
- `WORKER_ROLE_HINT` 环境变量(默认 "PRIMARY")
- V1 demo 单 worker 默认 PRIMARY,2 worker 一台起 PRIMARY 一台起 STANDBY

### 4.5 故障转移 @Scheduled

```java
@Scheduled(fixedDelay = 30_000) // 30s 一扫
public void scanPrimaryHealth() {
    // 1. 查所有 PRIMARY + status=online + last_heartbeat_at < now-90s
    // 2. 标 unhealthy (status=unhealthy)
    // 3. 查同 env_id 第一个 STANDBY + status=online
    // 4. 升级 → PRIMARY
    // 5. log 告警 + 通知(shipyard log + M11 接 AlertManager)
}
```

shipyard 启 schedule 任务(`@EnableScheduling`),跟 M5 业务定时任务同一框架。

---

## 5. ClusterRole 写权限

M8.1 ClusterRole 当前是 cluster-wide 只读 + 无 deploy 写权限。V2 改:

```yaml
rules:
  # 读 (M8.1 已有, 不变)
  - apiGroups: [""]
    resources: ["namespaces"]
    verbs: ["get", "list", "watch"]
  - apiGroups: [""]
    resources: ["pods", "pods/log"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["apps"]
    resources: ["deployments", "replicasets"]
    verbs: ["get", "list", "watch"]
  # 写 (M9 新增, 限定 shipyard-* ns)
  - apiGroups: [""]
    resources: ["configmaps", "services"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
  - apiGroups: ["apps"]
    resources: ["deployments"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
```

注:**ClusterRole 是 cluster-wide 资源,verbs 不能限定 ns**,但**RoleBinding/ClusterRoleBinding 限定 subject**。
M9 接受 cluster-wide 写权限 + shipyard 后端强制只对 `shipyard-{env_name}` ns apply(RBAC 锁不住 shipyard-* ns,但 shipyard 端校验 ns 前缀)。

V1.5 改: 用 Role + RoleBinding 限定每 env ns,但 M9 简版不做。

---

## 6. 前端

### 6.1 /projects/{id}/deployments 页

```
┌─────────────────────────────────────────────┐
│ Project: my-app                             │
│ [Tab: 详情] [Tab: 构建历史] [Tab: 部署历史] │
├─────────────────────────────────────────────┤
│ 部署历史 (deployments)                      │
│ ┌─────────────────────────────────────────┐ │
│ │ 时间   | env  | 镜像     | 状态  | 操作  │ │
│ │ 10:23  | dev  | nginx:1.27| SUCCESS|详情│ │
│ │ 10:15  | dev  | nginx:1.26| SUCCESS|详情│ │
│ │ 10:00  | dev  | nginx:1.25| FAILED |详情│ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ [+ 部署] → 弹 DeployDetail modal          │
└─────────────────────────────────────────────┘
```

### 6.2 DeployDetail modal

```
┌─ 部署 my-app 到 dev ────────────────┐
│ [简单模式]  [高级模式]              │
│                                     │
│ 简单模式:                          │
│   BuildRecord: [选择: build#42]    │
│   Replicas:    [3]                 │
│   Port:        [80]                │
│   Env Vars:    [key=val, key=val]  │
│   [预览 yaml] → 弹 yaml + 实际 diff│
│                                     │
│ 高级模式:                          │
│   [只读 yaml 预览]                 │
│   [跟 k8s live manifest diff]      │
│                                     │
│ [取消]              [部署]         │
└─────────────────────────────────────┘
```

### 6.3 Workers.vue 改

- 加 `role` badge(PRIMARY 绿色 / STANDBY 黄色)
- stat 加 "Primary 数 / Standby 数"
- 行加 "promote to primary" 按钮(STANDBY 行,手动升级,V1.5)

### 6.4 Dashboard.vue 改

- "Worker 管理" stat 加 "Primary 在线" 子数
- 加 "最近部署" 卡片(top 5)

### 6.5 路由

```
/projects/:id/deployments
/projects/:id/deployments/:deployId
```

---

## 7. E2E (test-m9-1.ps1)

PC 端家里真集群,验证:
1. shipyard 起服务 + worker primary 1 + standby 1 注册
2. curl POST /api/projects/1/deployments {imageTag: nginx:1.27, envId: shanghai-dev}
3. 验 deploy_record.status=SUCCESS + deploy_snapshot 有 1 行
4. curl 调 worker 拿 live manifest,验跟 shipyard 渲染的 yaml 等价
5. 修改镜像 nginx:1.28,触发第二次 deploy
6. 验 snapshot 列表 2 条 + current_snapshot_id 指向第二条
7. 触发 rollback 到 snapshot 1
8. 验 live manifest 回到 nginx:1.27

工时:30 分钟(主要是 docker / k3s 启动 + curl 跟 sleep)

---

## 8. 实施顺序 (14 commit, 3-4 天)

| # | commit | 内容 | 验收 |
|---|---|---|---|
| 1 | V2 SQL | deploy_record + deploy_snapshot + worker.role + pipeline_template 加字段 | mvn compile + Flyway apply OK |
| 2 | Entity + Mapper | DeployEntity, DeploySnapshotEntity, DeployMapper, DeploySnapshotMapper, WorkerMapper 改(role 字段) | mvn test 过 |
| 3 | Renderer + StateMachine | DeployStatus enum, DeployTemplateRenderer(简单模式 1 个模板) | unit test 覆盖渲染 5 case |
| 4 | Worker self-elect | WorkerServiceImpl 加 role 选择 + WorkerRoleHint DTO 字段 | unit test 0/1/2+ online 3 case |
| 5 | WorkerClient 5 方法 | 跟 M8.2 同样风格,JDK HttpClient 5s+2 retry | unit test mock 端点 |
| 6 | DeployService + Controller | 7 端点,primary 选择,调 worker,状态机 | mvn test 过 |
| 7 | HeartbeatScanner | @Scheduled 30s,primary 抢占 | unit test 时间 mock |
| 8 | K8sClient 4 方法 | Apply/Rollback/Scale/GetManifest (DynamicClient + unstructured) | go test fake k8s 端点 |
| 9 | deploy handler | 3 端点 + role self-elect env | go test 过 |
| 10 | ClusterRole 扩 | k8s/dev/worker-deployment.yaml 改 | kubectl apply 验 |
| 11 | 前端 api + 页 | api/deployments.ts, DeployDetail.vue, Deployments.vue | pnpm typecheck 0 |
| 12 | Workers.vue role | badge + 计数 + Dashboard 联动 | pnpm test 过 |
| 13 | E2E | test-m9-1.ps1 PC 端真集群 | 8 步全过 |
| 14 | docs | PROGRESS.md 标 M9 done + KNOWN_ISSUES 补 | commit message 列具体 |

---

## 9. 暂不做 (留 V1.5)

- helm/kustomize 模板渲染
- 多集群部署(round-robin 多 worker, M9 只 1 primary 跑)
- 自动快照策略(M9 手动 trigger, M9.5 接 git push)
- "高级模式"可编辑 yaml(M9 只读预览 + diff)
- live_manifest snapshot 化(M9 存 shipyard 渲染完的,V1.5 加 k8s 真生效的)
- 每 env 独立 Role + RoleBinding(代替 ClusterRole cluster-wide 写)
- worker standby 预热(M9 备机空闲,V1.5 半干活)
- 自动 promote standby 走 shipyard API(M9 @Scheduled 扫,V1.5 加手动 promote UI)

---

## 10. 踩坑预判

1. **DynamicClient 解析 yaml**: 第一遍写会卡 kind 识别,需要 hardcode 常见 5 种(Deployment/Service/ConfigMap/StatefulSet/DaemonSet)
2. **namespace 自动创建**: 第一次 deploy 到 `shipyard-shanghai-dev` ns 不存在,要先建,worker Apply 前判 ns 存在否则建
3. **image pull policy**: shipyard 默认 `IfNotPresent`,但 PC 端家里集群没 harbor,本地镜像要 `Never` — V1 demo 用 `Never`
4. **port 冲突**: 同一 dev 环境多次 deploy 同名 service 会冲突 — V1 shipyard 强制 deploy name = `{project_name}-{env_name}`
5. **sha256 算 yaml 字符串**: shipyard 渲染完存字符串,sha256 算字符串,V1.5 加排序去重
