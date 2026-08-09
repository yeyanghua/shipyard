# Kubernetes 集群部署文档 (k8s 1.31 实战)

> M15 前的 k8s 集群基础环境搭建笔记. 用于项目内复习 + V1.5 真实环境重装参考.
>
> 目标: 3 台 Rocky Linux 9.6 VM 装 k8s 1.31.0 集群 (1 master + 2 worker), 网络插件 Calico v3.28.

---

## 1. 概述

- **时间**: ~半天 (含踩坑)
- **架构**: 1 master (control plane) + 2 worker
- **网络插件**: Calico v3.28
- **核心难点**: 国内拉镜像 (docker.io / registry.k8s.io / quay.io 部分被墙), 全链路走代理/国内源
- **VPN 代理**: 192.168.10.29:7890 (HTTP/HTTPS)

跟 "Hello World" k8s 教程不同, 本文档重点写**国内环境**的修法.

---

## 2. 基础设施

### 2.1 节点清单

| 节点 | IP | 角色 | 规格 | OS |
|------|----|------|------|----|
| k8s-master | 192.168.91.138 | control plane | 4C8G | Rocky Linux 9.6 (Blue Onyx) |
| k8s-node1 | 192.168.91.139 | worker | 4C8G | Rocky Linux 9.6 (Blue Onyx) |
| k8s-node2 | 192.168.91.140 | worker | 4C8G | Rocky Linux 9.6 (Blue Onyx) |

### 2.2 网络

- 内网互通: 192.168.91.0/24
- DNS: 192.168.91.2 (公司内网, hostname 解析不全, 后面会加 `/etc/hosts`)
- VPN 代理: 192.168.10.29:7890 (HTTP, 不走 SOCKS5)
- 集群网段:
  - pod cidr: `192.168.0.0/16` (Calico)
  - service cidr: `10.96.0.0/12` (k8s 默认)
  - node 网络 = 内网 192.168.91.0/24

### 2.3 关键软件版本

| 组件 | 版本 |
|------|------|
| kernel | 5.14.0-687.10.1.el9_8.0.1.x86_64 |
| containerd | 2.3.3 (走 docker-ce 源) |
| kubeadm | 1.31.0 |
| kubelet | 1.31.0 |
| kubectl | 1.31.0 |
| Calico | v3.28.0 |

---

## 3. 阶段 0 — 基础准备 (所有 3 台节点都跑)

### 3.1 镜像源策略 (国内环境)

| 镜像类型 | 走哪里 | 备注 |
|---------|--------|------|
| docker-ce RPM | `https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo` | 阿里云 mirror, 国内通 |
| containerd.io RPM | docker-ce 仓库 | Rocky 9 默认源**没有** containerd 包 |
| k8s 组件镜像 (pause 等) | `registry.aliyuncs.com/google_containers` | kubeadm init `--image-repository` |
| calico 镜像 | `quay.io/calico/*` (ctr pull) + tag `docker.io/calico/*` 别名 | 见 4.2 |
| docker.io 其他业务镜像 | `192.168.10.29:7890` HTTP 代理 (containerd mirror) | 见 3.5 |

> **为什么不全走阿里云**: azk8s.cn / daocloud 镜像列表 API 经常 401/403, 而且 calico 不在 azk8s.cn 镜像列表里. quay.io 是 Calico 官方源, 国内能直接拉.

### 3.2 docker-ce + containerd 装

```bash
# 1. 阿里云 docker-ce repo
dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo

# 2. 装 containerd (不装 docker, k8s 用 containerd)
dnf install -y containerd.io

# 3. 启用 containerd
systemctl enable --now containerd
```

### 3.3 containerd 配置 (关键!)

`/etc/containerd/config.toml` 几处必改:

```toml
# 1. sandbox_image 走阿里云 (registry.k8s.io 国内 fail)
[plugins."io.containerd.grpc.v1.cri"]
  sandbox_image = "registry.aliyuncs.com/google_containers/pause:3.10"

# 2. SystemdCgroup = true (kubelet 用 systemd cgroup driver)
[plugins."io.containerd.grpc.v1.cri".containerd]
  [plugins."io.containerd.grpc.v1.cri".containerd.runtimes]
    [plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runc]
      runtime_type = "io.containerd.runc.v2"
      [plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runc.options]
        SystemdCgroup = true
```

应用:
```bash
systemctl restart containerd
```

### 3.4 (worker 节点) containerd mirror 走代理

`/etc/containerd/config.toml` 加:

```toml
[plugins."io.containerd.grpc.v1.cri".registry]
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors]
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]
      endpoint = ["http://192.168.10.29:7890"]
```

> ⚠️ **不是 `hosts.toml`**: `certs.d/*.hosts.toml` 是 TLS 证书配置, 不是 mirror. mirror 在 `config.toml` 里.

### 3.5 kubeadm / kubelet / kubectl 装

```bash
# 1. 阿里云 k8s repo
cat > /etc/yum.repos.d/kubernetes.repo <<EOF
[kubernetes]
name=Kubernetes
baseurl=https://mirrors.aliyun.com/kubernetes/yum/repos/kubernetes-el7-x86_64/
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=https://mirrors.aliyun.com/kubernetes/yum/doc/yum-key.gpg https://mirrors.aliyun.com/kubernetes/yum/doc/rpm-package-key.gpg
EOF

# 2. 装 1.31.0
dnf install -y kubeadm-1.31.0 kubelet-1.31.0 kubectl-1.31.0

# 3. 锁定版本 (避免 dnf upgrade 升)
dnf versionlock add kubelet kubeadm kubectl

# 4. 启用 kubelet (等 kubeadm init 后才会真起)
systemctl enable kubelet
```

### 3.6 系统配置 (所有节点)

```bash
# 1. 关 swap
swapoff -a
sed -i '/ swap / s/^/#/' /etc/fstab

# 2. 加载内核模块
cat > /etc/modules-load.d/k8s.conf <<EOF
overlay
br_netfilter
EOF
modprobe overlay
modprobe br_netfilter

# 3. sysctl
cat > /etc/sysctl.d/k8s.conf <<EOF
net.bridge.bridge-nf-call-iptables = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward = 1
EOF
sysctl --system
```

### 3.7 VPN 代理脚本 (所有节点)

`/etc/profile.d/vpn-proxy.sh`:

```bash
export http_proxy=http://192.168.10.29:7890
export https_proxy=http://192.168.10.29:7890
export HTTP_PROXY=http://192.168.10.29:7890
export HTTPS_PROXY=http://192.168.10.29:7890

# 内网不走代理 (重要! 集群通信走内网)
export no_proxy=localhost,127.0.0.1,192.168.91.0/24,192.168.0.0/16,10.96.0.0/12,10.244.0.0/16,.local,.internal
export NO_PROXY="$no_proxy"
```

应用:
```bash
source /etc/profile.d/vpn-proxy.sh
echo $HTTP_PROXY  # 应该看到 192.168.10.29:7890
```

> ⚠️ **NO_PROXY 必须排除所有内网网段**: 包括 pod cidr (192.168.0.0/16) + service cidr (10.96.0.0/12) + calico 内部 (10.244.0.0/16). 不然 kubelet/etcd 通信会走代理失败.

### 3.8 /etc/hosts (DNS 不全的环境)

```bash
cat >> /etc/hosts <<EOF
192.168.91.138 k8s-master
192.168.91.139 k8s-node1
192.168.91.140 k8s-node2
EOF
```

每台都加, 避免 DNS 解析失败 (warning 一直烦).

---

## 4. 阶段 1 — Master init (192.168.91.138)

### 4.1 kubeadm init

```bash
# 1. 拉 master 所需镜像 (走国内源, kubeadm 自动拉)
kubeadm config images pull \
  --image-repository=registry.aliyuncs.com/google_containers \
  --kubernetes-version=v1.31.0

# 2. init
kubeadm init \
  --apiserver-advertise-address=192.168.91.138 \
  --image-repository=registry.aliyuncs.com/google_containers \
  --kubernetes-version=v1.31.0 \
  --pod-network-cidr=192.168.0.0/16 \
  --service-cidr=10.96.0.0/12

# 3. 配置 kubectl
mkdir -p $HOME/.kube
cp -f /etc/kubernetes/admin.conf $HOME/.kube/config
chown $(id -u):$(id -g) $HOME/.kube/config
```

成功会看到 join 命令, 类似:
```
kubeadm join 192.168.91.138:6443 --token nhu3dl.xxxxx \
  --discovery-token-ca-cert-hash sha256:xxxxx
```

**保存好这个 token, worker join 用. token 默认 24h 过期.**

### 4.2 Calico v3.28 安装 (国内镜像)

```bash
# 1. 拉 Calico 镜像 (quay.io 国内能拉)
sudo ctr -n k8s.io images pull quay.io/calico/cni:v3.28.0
sudo ctr -n k8s.io images pull quay.io/calico/node:v3.28.0
sudo ctr -n k8s.io images pull quay.io/calico/kube-controllers:v3.28.0

# 2. 打 docker.io 别名 (Calico daemonset 默认从 docker.io 拉)
sudo ctr -n k8s.io images tag quay.io/calico/cni:v3.28.0 docker.io/calico/cni:v3.28.0
sudo ctr -n k8s.io images tag quay.io/calico/node:v3.28.0 docker.io/calico/node:v3.28.0
sudo ctr -n k8s.io images tag quay.io/calico/kube-controllers:v3.28.0 docker.io/calico/kube-controllers:v3.28.0

# 3. 装 Calico
kubectl apply -f https://raw.githubusercontent.com/projectcalico/calico/v3.28.0/manifests/calico.yaml

# 4. 给 master 写 calico/nodename (calico daemonset 启动要这个文件)
echo "k8s-master" | sudo tee /var/lib/calico/nodename
```

等 1-2 分钟, Calico pod 全部 Running:
```bash
kubectl get pods -n kube-system
# calico-kube-controllers-xxx Running
# calico-node-xxx (3 个, master + 2 worker) Running
# coredns-xxx (2 个) Running
```

---

## 5. 阶段 2 — Worker join (node1 + node2)

**两台 worker 同时跑下面这套**:

```bash
# 1. 预拉 Calico 镜像 (跟 master 一样)
sudo ctr -n k8s.io images pull quay.io/calico/cni:v3.28.0
sudo ctr -n k8s.io images pull quay.io/calico/node:v3.28.0
sudo ctr -n k8s.io images pull quay.io/calico/kube-controllers:v3.28.0

sudo ctr -n k8s.io images tag quay.io/calico/cni:v3.28.0 docker.io/calico/cni:v3.28.0
sudo ctr -n k8s.io images tag quay.io/calico/node:v3.28.0 docker.io/calico/node:v3.28.0
sudo ctr -n k8s.io images tag quay.io/calico/kube-controllers:v3.28.0 docker.io/calico/kube-controllers:v3.28.0

# 2. pause 镜像 (kubelet 启动要)
sudo ctr -n k8s.io images pull registry.aliyuncs.com/google_containers/pause:3.10

# 3. join
kubeadm join 192.168.91.138:6443 --token <token> \
  --discovery-token-ca-cert-hash sha256:<hash>
```

> Token 过期: master 跑 `kubeadm token create --print-join-command` 重新生成.

---

## 6. 阶段 3 — 验证

```bash
# 1. 节点状态
kubectl get nodes -o wide
# NAME         STATUS   ROLES           AGE   VERSION   INTERNAL-IP
# k8s-master   Ready    control-plane   26m   v1.31.0   192.168.91.138
# k8s-node1    Ready    <none>          11m   v1.31.0   192.168.91.139
# k8s-node2    Ready    <none>          11m   v1.31.0   192.168.91.140

# 2. 系统 pod
kubectl get pods -n kube-system

# 3. 跑个测试 pod
kubectl run nginx --image=nginx:alpine
kubectl get pods -o wide  # 应该看到 IP 192.168.x.x
kubectl delete pod nginx
```

如果某个 worker 一直 `NotReady`, 99% 是 calico 镜像没拉下来. 跑:
```bash
journalctl -u kubelet -n 50
sudo ctr -n k8s.io images list | grep calico
```

---

## 7. 阶段 4 — 待办 (M15 shipyard 集成)

| 任务 | 用途 | 预计时间 |
|------|------|----------|
| ingress-nginx | 集群入口 | 5 min |
| MetalLB | LoadBalancer (或直接 NodePort) | 10 min |
| drone runner | CI runner (接 shipyard backend) | 30 min |
| Harbor | 镜像仓库 | 1 h |
| Prometheus + Grafana | 监控 | 1 h |
| cert-manager | TLS 证书 | 15 min |

**M15 shipyard 集成**: shipyard 后端 + 前端 + drone runner 装到 k8s 集群, 通过 NodePort 或 ingress 暴露.

---

## 8. 踩坑合集 (11 条, 按重要程度排序)

### 8.1 docker.com SSL fail (国内)

- **症状**: `curl https://download.docker.com/...` SSL handshake failed
- **根因**: docker.com 域名被墙
- **修法**: 走阿里云 mirror
  ```bash
  dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
  ```

### 8.2 Rocky 9 默认源无 `containerd`

- **症状**: `dnf install containerd` No match
- **根因**: Rocky 9 AppStream 源没有 containerd 包
- **修法**: 走 docker-ce 仓库装 `containerd.io`

### 8.3 `registry.k8s.io/pause:3.10.2` 国内 fail

- **症状**: kubelet 启动报 `Failed to pull image "registry.k8s.io/pause:3.10.2"`
- **根因**: registry.k8s.io 解析到 Google IP (64.233.187.82), 国内不通
- **修法**: containerd config 改 `sandbox_image = "registry.aliyuncs.com/google_containers/pause:3.10"`
  ```bash
  sudo sed -i 's|sandbox_image = ".*"|sandbox_image = "registry.aliyuncs.com/google_containers/pause:3.10"|' /etc/containerd/config.toml
  sudo systemctl restart containerd
  ```

### 8.4 `docker.io/calico/cni` 国内 fail

- **症状**: `ImagePullBackOff` `docker.io/calico/cni:v3.28.0`
- **根因**: docker.io 解析到 Cloudflare IP (199.59.148.147), 部分被墙
- **修法**: 用 quay.io 拉 + tag 别名
  ```bash
  sudo ctr -n k8s.io images pull quay.io/calico/cni:v3.28.0
  sudo ctr -n k8s.io images tag quay.io/calico/cni:v3.28.0 docker.io/calico/cni:v3.28.0
  ```

### 8.5 `/var/lib/calico/nodename` 找不到

- **症状**: calico/node daemonset 报 `open /var/lib/calico/nodename: no such file or directory`
- **根因**: calico/node 启动要先读这个文件, daemonset 还没起来前 pod 不会写
- **修法**: master 自己 echo 一下
  ```bash
  echo "k8s-master" | sudo tee /var/lib/calico/nodename
  ```
  worker 上同理 (但 worker 的 calico/node pod 起来后会自己写, 一般不卡)

### 8.6 `tee` 父目录不存在

- **症状**: `tee /etc/containerd/certs.d/docker.io/hosts.toml` 报 No such file or directory
- **根因**: `certs.d/docker.io/` 目录不存在
- **修法**: 先 `mkdir -p` 到子目录层
  ```bash
  sudo mkdir -p /etc/containerd/certs.d/docker.io
  ```

### 8.7 sed 多行 `\n` 插入不生效

- **症状**: `sed -i '/pattern/a\\nnew line' file` 只插入一个空行, 不是多行
- **根因**: sed 的 `a` 命令不支持 `\n` 多行
- **修法**: 用 here-doc + 临时文件
  ```bash
  cat >> /etc/containerd/config.toml <<EOF
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors]
    ...
EOF
  ```
  或者用 Python heredoc (但 Rocky 9 python3 默认没装, 用 `dnf install -y python3`)

### 8.8 `hosts.toml` 不是配 mirror 的地方

- **症状**: 在 `certs.d/docker.io/hosts.toml` 写 `[host."http://192.168.10.29:7890"]` 但 containerd 没用
- **根因**: `hosts.toml` 是 **TLS 证书**配置 (跳过证书验证), 不是 mirror
- **修法**: mirror 配在 `/etc/containerd/config.toml` 的 `[plugins."io.containerd.grpc.v1.cri".registry.mirrors]`

### 8.9 PowerShell 5.1 ANSI 解码 UTF-8 中文乱码

- **症状**: `mvn spring-boot:run` 输出中文注释乱码
- **根因**: PowerShell 5.1 默认 ANSI 解码, UTF-8 中文注释被错误解析
- **修法**: e2e 脚本必须用 ASCII, 不用中文
  - 测试通过标志: `[PASS]` `[FAIL]`
  - 中文放在 description / echo 时不输出
  - 或: `chcp 65001` + `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8` (治标不治本)

### 8.10 azk8s.cn / daocloud 镜像列表 v2 API 401/403

- **症状**: `curl https://azk8s.cn/v2/_catalog` 返 401
- **根因**: 列表 API 限制访问, 但单镜像 pull 公开
- **修法**: 别用列表 API, 直接 pull 具体镜像 (如 `registry.aliyuncs.com/google_containers/pause:3.10`)

### 8.11 Calico 不在 azk8s.cn 镜像列表

- **症状**: 找 `azk8s.cn/calico/cni:v3.28.0` 报 not found
- **根因**: azk8s.cn 没同步 calico
- **修法**: 走 `quay.io/calico/cni:v3.28.0` (Calico 官方源, 国内能拉)

---

## 9. 配置文件位置速查

| 组件 | 路径 |
|------|------|
| containerd config | `/etc/containerd/config.toml` |
| containerd mirror hosts | `/etc/containerd/certs.d/*/hosts.toml` (TLS, 不是 mirror) |
| kubelet config | `/var/lib/kubelet/config.yaml` |
| kubelet flags | `/var/lib/kubelet/kubeadm-flags.env` |
| kubeconfig (master) | `/etc/kubernetes/admin.conf` |
| kubeconfig (worker) | `/etc/kubernetes/kubelet.conf` |
| Calico nodename | `/var/lib/calico/nodename` |
| CNI bin | `/opt/cni/bin/` |
| CNI net config | `/etc/cni/net.d/` |
| etcd data | `/var/lib/etcd/` |
| kubelet data | `/var/lib/kubelet/` |
| VPN proxy | `/etc/profile.d/vpn-proxy.sh` |

---

## 10. Cheat Sheet

### 10.1 节点

```bash
kubectl get nodes -o wide                    # 节点状态 + IP
kubectl describe node <name>                 # 节点详情 (含 taint, allocatable)
kubectl cordon <name>                        # 标记不可调度
kubectl drain <name> --ignore-daemonsets    # 驱逐 pod
kubectl uncordon <name>                      # 恢复调度
```

### 10.2 Pod

```bash
kubectl get pods -A                          # 所有 namespace
kubectl get pods -n <ns> -o wide             # 某 namespace, 含 IP/node
kubectl describe pod <name> -n <ns>          # pod 详情 (含 events)
kubectl logs <pod> -n <ns>                   # 容器日志
kubectl logs <pod> -n <ns> -c <container>    # 多容器 pod
kubectl logs <pod> -n <ns> -p                # 上一个实例 (重启后)
kubectl exec -it <pod> -n <ns> -- /bin/sh    # 进容器
kubectl port-forward <pod> 8080:80 -n <ns>   # 端口转发
```

### 10.3 Deployment / Service

```bash
kubectl get deploy -A
kubectl scale deploy/<name> -n <ns> --replicas=3
kubectl rollout status deploy/<name> -n <ns>
kubectl rollout undo deploy/<name> -n <ns>

kubectl get svc -A
kubectl expose deploy/<name> --port=80 --type=NodePort
```

### 10.4 故障排查

```bash
# kubelet 日志
journalctl -u kubelet -f

# containerd 日志
journalctl -u containerd -f

# 镜像列表
sudo ctr -n k8s.io images list

# 拉镜像
sudo ctr -n k8s.io images pull <image>
sudo ctr -n k8s.io images pull --platform linux/amd64 <image>  # 多平台

# 打 tag
sudo ctr -n k8s.io images tag <src> <dst>

# 删除镜像
sudo ctr -n k8s.io images rm <image>

# 资源占用
kubectl top nodes
kubectl top pods -A

# 事件
kubectl get events -A --sort-by=.lastTimestamp | tail -20
```

---

## 11. 复习要点 (按考频排序)

1. **国内镜像拉取全链路**: docker-ce 阿里云 + containerd pause 阿里云 + calico quay.io + docker.io 走代理. 三层都要改, 缺一不可.
2. **containerd SystemdCgroup = true**: kubelet 用 systemd cgroup driver, 不开 cgroupfs 模式会起不来.
3. **kubeadm init --image-repository 改阿里云**: 业务镜像走国内源, 不然 `registry.k8s.io` 必 fail.
4. **pod-network-cidr 跟 calico 一致**: kubeadm `--pod-network-cidr=192.168.0.0/16` 跟 calico.yaml `CALICO_IPV4POOL_CIDR` 必须一样.
5. **NO_PROXY 必须排除所有内网网段**: 包括 pod cidr + service cidr + calico 内部. 不然集群通信走代理失败.
6. **calico 镜像必须预拉到每台 worker**: daemonset 起不来的根因 99% 是镜像没拉. 用 quay.io + tag 别名.
7. **swap 必须关**: kubelet 启动会拒绝. `swapoff -a` + 注释 `/etc/fstab` 的 swap 行.
8. **/etc/hosts 加 master/worker 互相解析**: DNS 不全的环境, 不加会有 warning, 某些场景 (比如 coredns) 可能受影响.
9. **kubeadm token 默认 24h 过期**: 重新跑 `kubeadm token create --print-join-command`.
10. **CoreDNS 跑起来后集群才真 "可用"**: dns 是基础服务, coredns pod 没 Running 时, pod 内 `nslookup kubernetes.default` 会失败.

---

## 12. 参考资料

- [k8s 官方文档 - kubeadm 安装](https://kubernetes.io/docs/setup/production-environment/tools/kubeadm/install-kubeadm/)
- [Calico v3.28 安装](https://docs.tigera.io/calico/latest/getting-started/kubernetes/quickstart)
- [containerd 配置文档](https://github.com/containerd/containerd/blob/main/docs/cri/config.md)
- [shipyard DEPLOY-ENV.md](./DEPLOY-ENV.md) — M15 真实环境组件版本清单
- [shipyard PROGRESS.md](../PROGRESS.md) — 当前 milestone
