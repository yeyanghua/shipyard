# Worker register 跨网段 timeout — 排障留档 (2026-08-15)

> **背景**: 仔哥在做 M9.5 redesign 端到端验证, 改完所有代码后卡在
> "worker register 10s timeout" 这一关. 这个问题是 V1 demo 阶段 PC 主机
> (192.168.10.x) + 家里 k8s 集群 (192.168.91.x) **跨网段**部署带来的,
> 跟 M9.5 redesign 本身无关. 这份文档是**事故现场留档**, 留给 V1.5+
> 重新设计网络时参考.

---

## 1. 现象

- shipyard 后端在 D 盘本机 (192.168.10.29:8080)
- k8s 集群: master 192.168.91.138 / node1 192.168.91.139 / node2 192.168.91.140
- worker pod 跑在 139 / 140 上 (calico pod 网络 192.168.36.x / 192.168.169.x)
- worker 启动后 register 失败, log 10s timeout

```
WARN handler/register.go: register HTTP call failed
{"error": "Post \"http://...:8080/api/workers/register\": 
         context deadline exceeded"}
```

---

## 2. 排查过程 (按时间倒序)

### 阶段 1: 直连物理机 IP → 10s timeout

**配置**: `SHIPYARD_URL=http://192.168.10.29:8080`

**现象**: worker pod (calico 网络 192.168.36.x) 直接 POST 物理机 IP,
10s 后 client timeout.

**初步判断**: 跨网段问题. calico pod 网络 → 物理机 IP 路由不通.

**修法 (中间方案)**: 起 `socat` 跳板 DaemonSet, `hostNetwork + hostPort:30090`
转发 192.168.10.29:8080. master 本地 curl 30090 验证 200.

### 阶段 2: 跳板 work, 改用 svc DNS → 还是 10s timeout

**配置**: `SHIPYARD_URL=http://shipyard-tunnel.shipyard-tunnel.svc.cluster.local:30090`

**验证跳板**:
- ✅ master curl `192.168.91.139:30090/actuator/health` → 200 UP
- ✅ socat 进程在 pod 里跑, 监听 0.0.0.0:30090
- ✅ Service endpoint 列表完整 (192.168.91.139:30090, 192.168.91.140:30090)

**但 worker pod register 还是 10s timeout**.

**这阶段我 (Mavis) 绕了 2 个弯**:
1. 提议给 Service 加 `externalTrafficPolicy: Local` — 没用, 根因不在这里
2. 提议改 headless Service — 没用, 根因不在这里

**仔哥反馈 master curl svc DNS 报 `Could not resolve host`** — 这才让我意识到:
- master 不在 pod 网络, 不能解析 svc DNS 是正常的
- 真正应该验证的是 **worker pod 能不能解析 svc DNS** — 但我漏了这步
- 进一步推断: 即使 worker pod 能解析 svc DNS, 走 Service ClusterIP → kube-proxy
  DNAT 到 `192.168.91.139:30090` (物理机 IP) 仍然卡在同一跳:
  **calico pod 网络 → 物理网络**

### 阶段 3: 真正的 fix — worker pod 改 hostNetwork

**配置** (`k8s/dev/worker-deployment.yaml`):
- `spec.template.spec.hostNetwork: true` (跟 containers 同级)
- `SHIPYARD_URL=http://192.168.91.139:30090` (直连物理机 IP 形式)

**根因** (终于想清楚):
- worker pod 默认在 calico pod 网络 (192.168.36.x)
- 访问物理机 IP (192.168.91.x) 时, TCP 包能发出去 (calico 默认有出向路由)
- **但回包路径不对**: socat 在物理网卡上, 收到包后回包目标 IP 是 calico pod IP,
  物理网络不知道怎么回, 包丢失 → 10s timeout
- **hostNetwork: true** 让 worker pod 共享物理网络, 跟跳板 DaemonSet 一样,
  回包路径对得上 → work

**验证** (2026-08-15 12:20+):
- master 本地 curl `192.168.91.139:30090/actuator/health` → 200 UP
- hostNetwork worker pod 改完直连物理 IP → register 期望成功

---

## 3. 关键证据链

| 验证项 | 结果 | 结论 |
|---|---|---|
| socat 进程 | `1 root socat TCP-LISTEN:30090,fork,reuseaddr TCP:192.168.10.29:8080` | 跳板 work |
| 物理机 ss -tlnp | `0.0.0.0:30090 LISTEN 1/socat` | 跳板监听对 |
| master curl 物理 IP:30090 | `HTTP/1.1 200, {"status":"UP"...}` | 跳板链路 work |
| master curl svc DNS | `Could not resolve host` | master 不在 pod 网络, 正常 |
| Service ClusterIP | `10.96.75.198` (ClusterIP) | 名字解析层 OK |
| Service endpoint | `192.168.91.139:30090, 192.168.91.140:30090` | 物理机 IP 形式, 走 kube-proxy DNAT 后会卡 calico → 物理网一跳 |
| worker pod 走 svc DNS | (未验证, 但推断 timeout) | calico pod 网络 → 物理网 跨网段 timeout |
| **fix: hostNetwork + 直连物理 IP** | (待验证) | **期望 work** |

---

## 4. 经验教训

### 4.1 给 Mavis 自己 (agent 反思)

1. **跨网段网络问题要分层验证, 不能跳**:
   - DNS 解析层 (nslookup)
   - Service 路由层 (get svc / get endpoints)
   - Pod 网络 → 物理网络 路由层 (calico / kube-proxy)
   - 物理机本地回包路径 (ss -tlnp / curl 本机)
   **这次我漏了第 1 步, 也没让用户验证 worker pod 内的 DNS, 浪费了 1-2 轮**

2. **"在某台机器上 curl 200" 不等于 "在 pod 内 curl 200"**:
   - master / 物理机本地 curl 走的是**物理网络栈**
   - pod 内 curl 走的是 **calico pod 网络栈**
   - 两者是**完全不同的网络路径**, pod 内的访问可能要 SNAT / 路由 / iptables 配合
   **下次直接进 pod exec 验证, 不要在 master 上猜**

3. **"加一行配置可能能 fix" 的方案要谨慎提**:
   - 我连提了 `externalTrafficPolicy: Local` 和 `headless Service` 两个方案
   - 都是基于"应该是 kube-proxy / iptables 问题"的猜测
   - 但根因在更基础的"calico pod 网络 → 物理网络"路由问题
   **根因没定位前, 方案猜测会浪费用户操作时间**

### 4.2 给后续 V1.5+ 的人

**V1 demo 阶段用 hostNetwork 是 OK 的, 但生产前要换掉**:

| 场景 | 方案 |
|---|---|
| V1 demo (1 env 1 worker 跨网段) | hostNetwork + 直连物理 IP (当前方案) |
| V1.5 (多 env 多 worker 集群内) | 全部跑在 k8s 内, shipyard 也是 k8s pod, 用 ClusterIP / svc DNS |
| 生产 (跨 VPC / 跨 region) | 用 Ingress / Service Mesh / API Gateway, 别用 hostNetwork |

**hostNetwork 的副作用**:
- ⚠️ 容器端口 (8888) 绑物理网卡, replicas > 1 时跨 node 调度可避免冲突, 同 node 必冲突
- ⚠️ 失去 pod 网络隔离, worker 能访问物理机所有端口
- ⚠️ `status.podIP` 变成物理机 IP, 跟 WORKER_PUBLIC_URL 语义混淆
- ⚠️ DNS 解析走物理机 /etc/resolv.conf, 不是 CoreDNS

**更高保真的替代方案** (V1.5+):
- shipyard 后端也跑在 k8s 内, 用 ClusterIP 互相访问
- 跨网段用 Ingress / LoadBalancer
- 真实云环境用云厂商的 VPC peering / 内网 LB

### 4.3 给 memory 的沉淀

- **calico pod 网络 → 物理网络** 跨网段单向通, 反向不通 (回包路径)
- **hostNetwork: true** 解决"pod 访问物理机" 的最简方案, 但有副作用
- **svc DNS 解析在 master / 物理机上失败是正常的**, master 不在 pod 网络
- **任何"物理机 curl 200, pod 内 curl timeout" 的问题, 大概率是 calico 路由**, 不是 DNS / Service

---

## 5. 后续验证清单

- [ ] hostNetwork 改完后 worker register 成功 (DB worker 表出现 1 行 ONLINE)
- [ ] 浏览器 `/workers` 显示 ONLINE
- [ ] 点详情看 4 个集群代理接口 (ns / pods / deployments / worker-pods) 全 work
- [ ] 第二次心跳 (30s 后) 也 work
- [ ] replicas=2 时 2 个 pod 都 register 成功 (跨 node 安全)
- [ ] 改完的 manifest commit 进 git + push
