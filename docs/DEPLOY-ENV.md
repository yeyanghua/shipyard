# shipyard — M15 真实环境组件版本清单

> **TL;DR**: 你自己 k8s 部署要装 6 个组件的版本/镜像/Helm chart 清单.
> shipyard 后端 + 前端 + MySQL/Redis 见 [`docs/DEPLOY.md`](DEPLOY.md), 这是 **外部依赖** 清单.

---

## 1. k8s 集群 (用户自己管理)

| 项 | 推荐版本 | 备注 |
|---|---|---|
| k8s | **1.31.x** (stable 2026-08) | 1.30 也行 (LTS), 1.28 兼容性最好 |
| kubectl | **1.31.x** | 跟集群 minor 一致 |
| helm | **3.16.x** | Helm 3 (Helm 2 已 EOL 2026-01) |
| containerd | 1.7.x | 替代 Docker container runtime |
| kubelet | 1.31.x | 跟 k8s 一起装 |

**k8s 发行版** (任选一):
- kubeadm 自己装: 灵活, 1.31.x 最新
- Rancher RKE2: 企业级, k8s 1.31.x
- OpenShift 4.16: 红帽生态, 内置 Prometheus/Grafana/ArgoCD
- kind (本地): dev/test, k8s 1.31
- k3s: 你之前 V1 spec 提了, 但你说用 k8s, **不上 k3s**

**最小资源** (shipyard demo):
- 1 master + 1 worker (够 demo)
- master: 2 CPU / 4GB RAM
- worker: 4 CPU / 8GB RAM (跑 shipyard 后端 + drone runner + MySQL)

---

## 2. Drone CI (构建引擎)

| 项 | 版本 | 备注 |
|---|---|---|
| drone/drone | **2.23.0** (latest stable 2026-08) | 官方镜像 `drone/drone:2.23.0` |
| drone runner docker | **1.8.3** | `drone/drone-runner-docker:1.8.3` |
| drone/drone Helm chart | `drone/drone:0.7.0` | helm repo add drone https://charts.drone.io |

**关键 config**:
```yaml
# drone values.yaml (helm)
env:
  DRONE_GITHUB_SERVER: https://github.com  # 改你的 git server
  DRONE_RPC_SECRET: <random 32 byte>       # shipyard <-> drone 共享 secret
  DRONE_SERVER_HOST: drone.shipyard.local
  DRONE_SERVER_PROTO: https
  DRONE_USER_CREATE: username:admin,admin:true
  DRONE_DATABASE_DRIVER: postgres
  DRONE_DATABASE_DATASOURCE: postgres://...
```

**shipyard 怎么用** (M15):
- shipyard 后端调 `POST /api/v1/repos/{owner}/{repo}/builds` 触发 build, 带 `DRONE_RPC_SECRET` HMAC
- drone runner 跑 pipeline step (compile / test / docker build / docker push Harbor)
- drone 回调 shipyard `/webhook/drone`, shipyard 验签 + 落 `build_record` + `build_log`

---

## 3. Harbor (镜像仓库)

| 项 | 版本 | 备注 |
|---|---|---|
| goharbor/harbor | **v2.12.0** (2026-08) | 主镜像 `goharbor/harbor:v2.12.0` |
| goharbor/harbor-helm | **1.15.0** | helm chart |
| database | PostgreSQL 16.x | Harbor 2.10+ 必须 PG, 不再支持内置 MySQL |
| redis | 7.2.x | Harbor 内部 cache |

**helm install**:
```bash
helm repo add harbor https://helm.goharbor.io
helm install harbor harbor/harbor --namespace harbor --create-namespace \
  --set expose.ingress.hosts.core=harbor.shipyard.local \
  --set externalURL=https://harbor.shipyard.local \
  --set harborAdminPassword=<random>
```

**shipyard 怎么用** (M15):
- 后端配置 `shipyard.drone.image-tag-template` 拼出 `harbor.shipyard.local/<project>/<repo>:<tag>`
- drone pipeline `docker push harbor.shipyard.local/...` 推镜像
- shipyard worker 拉镜像 `docker pull harbor.shipyard.local/...`
- shipyard 存镜像 URL 在 `build_record.harbor_image_url`

---

## 4. Prometheus (监控采集)

| 项 | 版本 | 备注 |
|---|---|---|
| prom/prometheus | **v2.55.0** (2026-08) | 主镜像 `prom/prometheus:v2.55.0` |
| prom/pushgateway | v1.10.0 | (可选) shipyard 自定义指标 push |
| prom/node-exporter | v1.8.2 | 节点指标 |
| kube-prometheus-stack (Helm) | **67.x** | 一键装 prom + alertmanager + grafana |
| prometheus Helm chart | `prometheus-community/kube-prometheus-stack:67.4.0` |

**shipyard 暴露指标** (M13):
- 后端 `/actuator/prometheus` 端点, Spring Boot 3 自带 micrometer
- 自定义指标: build count by status, build duration p50/p95/p99, env_var 解密失败计数
- k8s 集群用 prometheus-operator 自动抓 `ServiceMonitor`

---

## 5. Grafana (仪表盘)

| 项 | 版本 | 备注 |
|---|---|---|
| grafana/grafana | **11.3.0** (2026-08) | 主镜像 `grafana/grafana:11.3.0` |
| grafana/grafana Helm chart | `grafana/grafana:8.6.0` | helm repo add grafana https://grafana.github.io/helm-charts |
| prometheus datasource | 内置 | 自动加 Prometheus data source |

**shipyard 推荐 dashboard** (M13 写):
- Build 趋势 (按项目 / 按状态 / 按天)
- Build 耗时 p50/p95/p99
- 失败 build top 10
- env_var 解密失败告警
- JVM 指标 (heap / GC / thread)

---

## 6. Alertmanager (告警 - 可选)

| 项 | 版本 | 备注 |
|---|---|---|
| prom/alertmanager | **v0.27.0** | 主镜像 `prom/alertmanager:v0.27.0` |

**shipyard V1 告警规则** (M13):
- shipyard 后端 5xx 错误率 > 1%
- build 失败率 > 30% (近 1 小时)
- env_var 解密失败 > 0
- MySQL 连接池 > 80% 占用
- JVM heap > 90% 持续 5 分钟

---

## 7. 整合镜像版本速查表 (一键复制)

```yaml
# M15 真实环境组件版本 (2026-08 时点)
kubernetes: 1.31.x
kubectl: 1.31.x
helm: 3.16.x
containerd: 1.7.x

# 构建
drone_server: drone/drone:2.23.0
drone_runner_docker: drone/drone-runner-docker:1.8.3

# 镜像仓库
harbor: goharbor/harbor:v2.12.0
harbor_db: postgres:16-alpine
harbor_redis: redis:7.2-alpine

# 监控
prometheus: prom/prometheus:v2.55.0
grafana: grafana/grafana:11.3.0
alertmanager: prom/alertmanager:v0.27.0
pushgateway: prom/pushgateway:v1.10.0  # 可选
node_exporter: prom/node-exporter:v1.8.2  # 可选

# Helm charts
kube-prometheus-stack: prometheus-community/kube-prometheus-stack:67.4.0
harbor_helm: goharbor/harbor:1.15.0
drone_helm: drone/drone:0.7.0
```

---

## 8. shipyard V1 demo 最小依赖 (你现在装)

如果只想 demo 端到端跑通, **只需要 1+2+3+4+5** (k8s + drone + harbor + prometheus + grafana), alertmanager 可选.

完整 demo 周 (M15) 1 master + 1 worker 资源:
- 集群: 6 CPU + 12 GB RAM
- shipyard 后端: 1 CPU + 1 GB
- shipyard web: 0.5 CPU + 256 MB
- MySQL: 1 CPU + 2 GB
- Redis: 0.5 CPU + 256 MB
- Drone server: 0.5 CPU + 512 MB
- Drone runner: 1 CPU + 1 GB
- Harbor: 1 CPU + 1 GB (+ PG + Redis 各 256 MB)
- Prometheus: 0.5 CPU + 512 MB
- Grafana: 0.5 CPU + 256 MB

总: ~7 CPU / 13 GB — 1 master 4C/8G + 1 worker 4C/8G 够用.

---

## 9. 装机顺序 (M15 推荐)

1. k8s 集群 (kubeadm/RKE2)
2. helm 装 ingress-nginx (集群入口)
3. cert-manager (TLS)
4. MySQL + Redis (StatefulSet) — shipyard 数据
5. Harbor (镜像仓库) — shipyard worker 拉镜像用
6. Drone server + drone runner (构建引擎)
7. shipyard 后端 + 前端 (Deployment) — **M15 真正接 drone/k8s**
8. Prometheus + Grafana (监控)
9. shipyard worker (Go 二进制, 部署到目标 k8s 集群拉镜像 + kubectl apply)

---

## 10. 装机后 shipyard 配置 (M15)

shipyard 后端 `application.yml` 加:

```yaml
shipyard:
  drone:
    mock-enabled: false   # 关 mock, 接真 drone
    server-url: https://drone.shipyard.local
    rpc-secret: ${SHIPYARD_DRONE_RPC_SECRET}
    image-tag-template: 'harbor.shipyard.local/${projectName}/${repoName}:${commitShaShort}'
  
  worker:
    enabled: true
    namespace: shipyard-build
    harbor-registry: harbor.shipyard.local
    
  security:
    demo-mode: false   # V1.5 走真实鉴权
```

---

## 联系 / 后续

- 文档: `docs/DEPLOY.md` (shipyard 自身) + `docs/DEPLOY-ENV.md` (本文件, 外部依赖)
- M15 真实集成代码: shipyard 后端 `DroneClient` interface 的 `MockDroneClient` → `RealDroneClient` 实现替换
- 真实 worker: `worker/` 目录 (M8 写, Go 二进制, shipyard-worker 部署到目标 k8s 集群)
