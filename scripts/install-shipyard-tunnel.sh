#!/bin/bash
# ============================================================
# shipyard-tunnel: worker 集群侧跳板, 把 worker outbound 流量
# 转发到 shipyard 控制面后端.
#
# 适用场景 (V1 简化):
#   - shipyard 跑家里 D 盘 (192.168.10.29:8080)
#   - worker 跑家里 k3d 真集群 (192.168.91.138~140)
#   - 跨家庭内网 + 跨 k8s pod 网络, 直连 timeout
#   - 用 k8s-master (192.168.91.138) 装 socat, listen 30090 → forward 到 192.168.10.29:8080
#
# 生产场景 (V1.5+, 跨地域 / 跨 VPC / 跨 NAT):
#   - 替换 socat 为 cloudflared / frp / ssh -R / nginx stream
#   - 证书换成 mTLS
#   - 跳板机器需要公网 IP / 域名, 跟 shipyard 端约定好
#
# 用法 (在跳板机上跑一次):
#   sudo bash scripts/install-shipyard-tunnel.sh
# ============================================================

set -euo pipefail

# === 1. 配置 (按你环境改) ===
SHIPYARD_HOST="${SHIPYARD_HOST:-192.168.10.29}"
SHIPYARD_PORT="${SHIPYARD_PORT:-8080}"
TUNNEL_PORT="${TUNNEL_PORT:-30090}"
SERVICE_NAME="shipyard-tunnel"

echo ">>> shipyard-tunnel install"
echo "    shipyard  : ${SHIPYARD_HOST}:${SHIPYARD_PORT}"
echo "    tunnel    : 0.0.0.0:${TUNNEL_PORT}"
echo ""

# === 2. 装 socat ===
if ! command -v socat &> /dev/null; then
  echo ">>> install socat"
  if command -v apt-get &> /dev/null; then
    apt-get update -qq && apt-get install -y -qq socat
  elif command -v yum &> /dev/null; then
    yum install -y socat
  elif command -v dnf &> /dev/null; then
    dnf install -y socat
  else
    echo "ERROR: no apt/yum/dnf found, install socat manually" >&2
    exit 1
  fi
else
  echo ">>> socat already installed: $(socat -V | head -1)"
fi

# === 3. systemd service ===
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
cat > "${SERVICE_FILE}" <<EOF
[Unit]
Description=shipyard-tunnel: forward worker outbound to shipyard backend
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/socat TCP-LISTEN:${TUNNEL_PORT},fork,reuseaddr TCP:${SHIPYARD_HOST}:${SHIPYARD_PORT}
Restart=always
RestartSec=3
StandardOutput=append:/var/log/shipyard-tunnel.log
StandardError=append:/var/log/shipyard-tunnel.log

[Install]
WantedBy=multi-user.target
EOF

echo ">>> systemd unit written: ${SERVICE_FILE}"

# === 4. enable + start ===
systemctl daemon-reload
systemctl enable "${SERVICE_NAME}"
systemctl restart "${SERVICE_NAME}"
sleep 1

# === 5. 验证 ===
echo ">>> service status:"
systemctl --no-pager status "${SERVICE_NAME}" | head -10
echo ""
echo ">>> local probe:"
curl -s -m 3 "http://127.0.0.1:${TUNNEL_PORT}/actuator/health" || echo "(shipyard 端没启 / 网络不通, 先确认 shipyard 端起来再回这里)"
echo ""
echo ">>> done. shipyard-tunnel installed."
echo "    worker SHIPYARD_URL=http://<this-host>:${TUNNEL_PORT}"
