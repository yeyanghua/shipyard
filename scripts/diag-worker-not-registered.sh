#!/bin/bash
# ============================================================
# 排查脚本: worker pod 跑着, 但 shipyard DB worker 表 0 行
#
# 跑在 k8s-master 上 (192.168.91.138)
# 假设: shipyard 后端在 192.168.10.29:8080, worker pod 在 shipyard ns
# ============================================================

set +e  # 不要因为单条失败停, 我要拿所有证据

SHIPYARD_HOST="${SHIPYARD_HOST:-192.168.10.29}"
SHIPYARD_PORT="${SHIPYARD_PORT:-8080}"
TUNNEL_PORT="${TUNNEL_PORT:-30090}"
NS="${NS:-shipyard}"

echo "=========================================="
echo "1. worker pod 调度情况 (看 IP + AGE)"
echo "=========================================="
kubectl -n "${NS}" get pod -l app=shipyard-worker -o wide

echo ""
echo "=========================================="
echo "2. worker pod env (SHIPYARD_URL / WORKER_TOKEN)"
echo "=========================================="
POD=$(kubectl -n "${NS}" get pod -l app=shipyard-worker -o name | head -1)
echo "POD=${POD}"
kubectl -n "${NS}" exec "${POD}" -- env 2>/dev/null | grep -E '^(SHIPYARD_URL|WORKER_TOKEN|WORKER_ENV|WORKER_PUBLIC_URL|K8S_IN_CLUSTER|NODE_NAME)=' | sort

echo ""
echo "=========================================="
echo "3. worker 进程 log (register / heartbeat / error)"
echo "=========================================="
kubectl -n "${NS}" logs "${POD}" --tail=80 2>&1 | grep -E 'register|heartbeat|error|warn' || echo "(no worker log yet)"

echo ""
echo "=========================================="
echo "4. shipyard 后端可达性 (从 k8s-master 节点视角)"
echo "=========================================="
echo "-- direct: shipyard_host:shipyard_port --"
curl -s -m 3 "http://${SHIPYARD_HOST}:${SHIPYARD_PORT}/actuator/health" || echo "(direct fail)"
echo ""
echo "-- tunnel: 127.0.0.1:tunnel_port (if installed) --"
curl -s -m 3 "http://127.0.0.1:${TUNNEL_PORT}/actuator/health" || echo "(tunnel not installed / fail)"

echo ""
echo "=========================================="
echo "5. pod IP 跟 k8s node 物理网络对比"
echo "=========================================="
echo "-- pod IP --"
kubectl -n "${NS}" get pod "${POD}" -o jsonpath='{.status.podIP}'
echo ""
echo "-- node 物理 IP --"
NODE=$(kubectl -n "${NS}" get pod "${POD}" -o jsonpath='{.spec.nodeName}')
kubectl get node "${NODE}" -o jsonpath='{.status.addresses[?(@.type=="InternalIP")].address}'
echo ""
echo "(pod IP 在 192.168.36.x / 192.168.169.x = calico pod 网络, 跟物理网卡不同网段)"

echo ""
echo "=========================================="
echo "6. shipyard DB worker 表 (通过 shipyard /api/workers 查)"
echo "=========================================="
curl -s -m 3 "http://${SHIPYARD_HOST}:${SHIPYARD_PORT}/api/workers?page=1&size=10" 2>&1 | head -3

echo ""
echo "=========================================="
echo "排查方向 (看上面 5 + 6 输出判断):"
echo "  - 如果 4 direct 通 + 4 tunnel 通, 但 worker log 还是 timeout"
echo "    → pod 走的是 calico 网络, 物理网卡通但 pod 网络不通"
echo "    → 修法: 在 k8s-master 上装 shipyard-tunnel (socat 30090 → shipyard 8080),"
echo "            改 SHIPYARD_URL=http://192.168.91.138:30090"
echo "  - 如果 4 direct 都 fail, shipyard 后端在 D 盘没起来 / 防火墙挡"
echo "    → 起 shipyard, 或 Windows Defender 加 inbound rule for 8080"
echo "  - 如果 5 显示 pod IP 跟 node IP 同一网段, 那是 flannel host-gw, 直连应该通"
echo "    → 查 worker log 看具体 error"
echo "  - 如果 6 已经有 worker row, 但 list 显示 0, 那是 V1 demo-mode token 问题或前端缓存"
