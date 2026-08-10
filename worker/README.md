# shipyard worker (Go)

> shipyard 部署执行器 — 跑在 k8s 集群里,通过 client-go 调 k8s API,接受 shipyard 后端 HTTP 指令.

## 设计原则 (来自 spec §5.3)

1. **不持久化状态** — 无 DB、无文件持久化、无业务数据内存缓存
2. **in-cluster ServiceAccount** — 自动从 `/var/run/secrets/kubernetes.io/serviceaccount/` 读 token
3. **任务执行用状态机** — 接任务前 shipyard 改 deploy_record.status=pending; worker 任务开始后改 running,完成改 success/failed
4. **失败原因结构化** — 返回 JSON `{ "code": "IMAGE_PULL_FAILED", "message": "...", "k8s_event": "..." }`
5. **持续状态上报** — apply 成功后 5 分钟内每 5s 报一次 pod status

## 当前进度

- [x] **M8.1**: 骨架 + mock 接口 (本机直跑,不调 k8s API) — `feat: M8.1 worker 骨架 + 5 mock 接口 + 单测`
- [ ] **M8.2**: shipyard 后端 WorkerController 调 worker
- [ ] **M8.3**: k3d 单节点部署 + 切真 client-go
- [ ] **M8.4**: 真 apply deployment yaml (从 shipyard 接收 snapshot)
- [ ] **M8.5**: 持续 pod 状态上报回 shipyard

## API 端点

| Method | Path | 用途 |
|---|---|---|
| GET | `/healthz` | k8s liveness probe |
| GET | `/readyz` | k8s readiness probe |
| GET | `/api/v1/cluster/namespaces` | 列出所有 namespace |
| GET | `/api/v1/cluster/pods?namespace=xxx` | 列出指定 ns 的 pod |
| GET | `/api/v1/cluster/deployments?namespace=xxx` | 列出指定 ns 的 deployment |
| GET | `/metrics` | Prometheus metrics |
| POST | `/api/v1/tasks/echo` | M8.1 测试用 — 收到 body 原样回,验证通信 |
| POST | `/api/v1/tasks/deploy` | M8.4+ — apply deployment yaml |
| POST | `/api/v1/tasks/rollback` | M8.4+ — 回滚到指定 deploy_record |
| POST | `/api/v1/tasks/stop` | M8.4+ — 停止卡住的部署 |

## 启动 (本机开发,不走 k8s)

```bash
cd worker
go mod tidy
go run ./cmd/worker
# 默认监听 :8888
# 调 mock 接口:
curl http://localhost:8888/healthz
curl http://localhost:8888/api/v1/cluster/namespaces
```

环境变量:

| 变量 | 默认 | 用途 |
|---|---|---|
| `WORKER_PORT` | `8888` | HTTP server 端口 |
| `WORKER_ENV` | `dev` | 环境名 (dev/test/prod) |
| `WORKER_NAME` | `worker-${HOSTNAME}` | worker 唯一名 |
| `SHIPYARD_URL` | `http://localhost:8080` | shipyard 后端地址 (注册/心跳用) |
| `K8S_IN_CLUSTER` | `false` | true = 用 in-cluster ServiceAccount;false = 用 $KUBECONFIG |

## 跑测试

```bash
go test ./...           # 全部
go test -v ./internal/handler  # verbose
go test -cover ./...    # 覆盖率
```

## Docker 构建

```bash
docker build -t shipyard-worker:dev -f worker/Dockerfile worker/
# multi-stage: golang:1.22-alpine AS build -> scratch AS runtime
# 预期镜像 ~20MB
```
