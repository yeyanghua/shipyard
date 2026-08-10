# M8 — shipyard worker (Go) 详细方案

> **作者**: Mavis (Mavis Code) · **日期**: 2026-08-10 · **状态**: M8.1 骨架完成 (本机跑通 mock 接口)
> 关联: 仔哥拍板 2026-08-10 下午定"先开发后部署"路径

---

## 0. TL;DR

把 shipyard 跟 k8s 集群解耦:shipyard 后端**完全不知道** k8s 存在,所有集群操作走 worker (Go) 代理。

```
[shipyard Java/Spring]                [worker (Go, in k8s pod)]              [k8s API]
       │                                    │                                    ▲
       │ HTTP JSON 调                        │ client-go (in-cluster)             │
       └────────────────────────────────────┴────────────────────────────────────┘
```

- shipyard Web 点"集群信息" → shipyard 后端 → worker (k8s in-cluster) → k8s API → 返回 ns/pods/deployments

---

## 决策固化

| 决策点 | 选择 | 影响 |
|---|---|---|
| **架构** | shipyard 不知 k8s 存在,所有集群操作走 worker | 后端零 k8s 依赖,worker 是唯一接触面 |
| **worker 语言** | Go 1.22+ | spec §5.1,跟 shipyard Java 解耦 |
| **worker 框架** | Gin | 流行 + 中间件丰富,跟你其他项目一致 |
| **k3s 部署方式** | k3d (Docker in Docker) | Mac 5s 起,资源占用 0.5GB,清掉也容易 |
| **第一阶段范围** | 只 mock,不真调 k8s API | 验证通信链路,部署后再切真 client-go |
| **数据格式** | `{code, message, data}` 跟 shipyard 对齐 | 业务码体系(非 HTTP status 主导) |

---

## 1. 范围与边界 (M8.1 第一阶段)

### 范围内

- worker Go 骨架(可本机 `go run` 跑起来)
- 5 个 mock HTTP 端点(`/healthz`, `/readyz`, `/api/v1/cluster/{namespaces,pods,deployments}`, `/api/v1/tasks/echo`)
- worker 主动注册到 shipyard + 30s 心跳
- 单元测试(全部 handler + config 覆盖)
- Dockerfile (multi-stage golang:1.22 → scratch, ~20MB)
- k3d 部署 manifest (Deployment + ServiceAccount + ClusterRole + ConfigMap + Secret + Service)

### 范围外 (后续 milestone)

- ❌ 真 apply deployment yaml — M8.4
- ❌ 持续 pod 状态上报 — M8.5
- ❌ 真实 k8s API 调用 (M8.1 全 mock) — M8.3 接 client-go
- ❌ Worker 鉴权 token 真用 — M8.2
- ❌ Prometheus metrics 暴露 — M13
- ❌ shipyard 后端 WorkerController — M8.2

---

## 2. 目录结构

```
worker/
├── cmd/worker/main.go              # 入口
├── internal/
│   ├── config/config.go            # env 配置加载
│   ├── log/log.go                  # zap logger 封装
│   ├── types/types.go              # 共享类型 (shipyard↔worker DTO)
│   ├── handler/
│   │   ├── cluster.go              # /cluster/* 端点 (mock)
│   │   ├── health.go               # /healthz, /readyz, /tasks/echo
│   │   ├── register.go             # worker 主动注册 + 心跳
│   │   └── *_test.go               # 单元测试
│   ├── server/server.go            # gin engine 装配 + 中间件
│   ├── k8sclient/                  # M8.3 接 client-go 留位置
│   └── client/                     # M8.4 worker 调 shipyard client 留位置
├── Dockerfile                      # multi-stage golang:1.22 → scratch
├── go.mod
├── README.md
└── .gitignore
```

---

## 3. 关键设计点

### 3.1 数据格式统一

shipyard 全栈用 `{code, message, data}` 业务码体系(HTTP 永远 200),worker 跟 shipyard 对齐:

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "workerName": "worker-dev-01",
    "received": { "message": "hello", "timestamp": 1234567890 },
    "processedAt": "2026-08-10T15:30:00Z"
  }
}
```

### 3.2 worker ID 注入

worker 启动后调 shipyard `/api/workers/register` 拿到 ID,后续心跳 + echo 回填。M8.2 时 shipyard 才实现这个端点,M8.1 worker 自己 mock 也能跑(拿到 0/未注册也工作)。

### 3.3 in-cluster ServiceAccount 最小权限

M8.1 阶段 ServiceAccount 配 ClusterRole 只读:`namespaces`/`pods`/`pods/log`/`deployments`/`replicasets`,verbs 仅 `get/list/watch`。M8.4 真正 apply 时再扩 `create/update/patch/delete`,且只对 `shipyard` namespace。

### 3.4 时区与时间格式

worker 用 `time.Now()` 默认 UTC,JSON 序列化 `time.Time` 走 RFC3339(Go 默认)。shipyard 后端 Jackson 配 `Asia/Shanghai` 时区,前端 dayjs 转本地。**别在 worker 端转时区**。

---

## 4. 端到端验证

### M8.1 (本机,不调 k8s)

```bash
cd worker
go mod tidy
go test ./...
go run ./cmd/worker &
# 5 秒后:
curl http://localhost:8888/healthz
curl http://localhost:8888/api/v1/cluster/namespaces
curl -X POST http://localhost:8888/api/v1/tasks/echo -H 'Content-Type: application/json' \
  -d '{"message":"hello","timestamp":1234567890}'
# 预期: 返 {code:0, data: {workerName, received, processedAt}}
```

### M8.2 (跟 shipyard 后端接通,worker 仍在 Mac)

1. 启动 shipyard 后端 (M5 已能跑)
2. shipyard 加 WorkerController (POST /api/workers/register, POST /api/workers/{id}/heartbeat, GET /api/workers/{id}/cluster/namespaces)
3. 启动 worker,设 `SHIPYARD_URL=http://localhost:8080`
4. 验证: shipyard `worker` 表有 2 行,last_heartbeat_at 30s 内

### M8.3 (worker 进 k3d, 切真 client-go)

1. `brew install k3d` (或用 rancher-desktop)
2. `k3d cluster create shipyard --agents 1`
3. `k3d image import shipyard-worker:dev -c shipyard`
4. `kubectl apply -f k8s/dev/worker-deployment.yaml`
5. 改 cluster handler 注入 K8sClient,调 client-go 返真数据

---

## 5. 踩坑留底

(等 M8.1 验完再回填,可能遇到:go mod tidy 慢、scratch 镜像本地不能跑需要 alpine 验证、k3d 网络 host.docker.internal 配置等)

---

## 6. 工时

- M8.1 (本骨架): 1-2 天
- M8.2 (shipyard 后端接通): 1 天
- M8.3 (k3d 部署 + client-go): 1-2 天
- M8.4 (真 apply): 2-3 天
- M8.5 (持续状态上报): 1 天

合计: **6-9 天**(M8 整段)
