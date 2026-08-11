# test-m9-1.ps1
# M9 端到端测试 — 部署到 PC 端真 k3s 集群 (M8.3c 验证过的 setup)
#
# 8 步覆盖 M9 核心链路:
#   1. shipyard health UP (后端在跑)
#   2. worker 已注册 + 30s 心跳 fresh + health=HEALTHY
#   3. 创建 deploy (POST /api/projects/{id}/deployments)
#   4. 等待 deploy.status = SUCCESS (worker 真 apply 到 k3s)
#   5. 验证 k8s 集群里真出现 Deployment/Service (kubectl get)
#   6. 列 snapshot (GET /api/deployments/{id}/snapshots)
#   7. 触发回滚 (POST /api/deployments/{id}/rollback/{snapshotId})
#   8. 验证回滚后 k8s 资源更新
#
# 怎么用 (PC 端):
#   1. cd D:\Projects\shipyard
#   2. git pull origin main
#   3. powershell -ExecutionPolicy Bypass -File scripts\test-m9-1.ps1
#
# 不需要管理员权限.
# 前置: shipyard :8080 在跑 + worker 已在 PC 端跑 + 家里 k3s 集群 OK (192.168.91.138)

$ErrorActionPreference = 'Stop'

# === 配置 (可改) ===
$SHIPYARD_URL = 'http://localhost:8080'
$PROJECT_ID   = 1                     # dev project (M5 demo 创过)
$ENV_ID       = 1                     # dev env
$WORKER_ID    = ''                    # 自动从 /api/workers 拿
$NAMESPACE    = 'shipyard-dev'        # M9 决策: shipyard-{env_name}
$DEPLOY_NAME  = 'shipyard-demo'       # M9 test 用的 deployment name
$IMAGE_TAG    = 'nginx:1.27-alpine'   # 用现成镜像, 跳过 build 链
$KUBECTL      = 'kubectl'             # 假设 PATH 里有
$TIMEOUT_SEC  = 60                    # 等 deploy SUCCESS 最大时长

# === ANSI 颜色 ===
function Write-Step($n, $msg) {
    Write-Host "`n[$n] " -NoNewline -ForegroundColor Cyan
    Write-Host $msg -ForegroundColor White
}
function Write-OK($msg)    { Write-Host "  ✓ " -NoNewline -ForegroundColor Green; Write-Host $msg }
function Write-Err($msg)   { Write-Host "  ✗ " -NoNewline -ForegroundColor Red;   Write-Host $msg }
function Write-Info($msg)  { Write-Host "  → $msg" -ForegroundColor Gray }

# === HTTP helper ===
function Invoke-Api($method, $url, $body = $null) {
    try {
        if ($body) {
            $json = $body | ConvertTo-Json -Depth 10
            return Invoke-RestMethod -Method $method -Uri $url -Headers @{'Content-Type' = 'application/json'} -Body $json -TimeoutSec 30
        } else {
            return Invoke-RestMethod -Method $method -Uri $url -TimeoutSec 30
        }
    } catch {
        Write-Err "API call failed: $($_.Exception.Message)"
        throw
    }
}

# ===== 1. shipyard health =====
Write-Step 1 "shipyard health check (后端 :8080)"
$healthResp = Invoke-RestMethod -Uri "$SHIPYARD_URL/actuator/health" -TimeoutSec 5
if ($healthResp.status -ne 'UP') {
    Write-Err "shipyard not UP: $healthResp.status"
    exit 1
}
Write-OK "shipyard UP"

# ===== 2. worker 已注册 + HEALTHY =====
Write-Step 2 "worker 注册 + 心跳 + health=HEALTHY"
$workers = Invoke-Api GET "$SHIPYARD_URL/api/workers?page=1&size=10"
$activeWorker = $workers.records | Where-Object { $_.status -eq 'ACTIVE' -and $_.health -eq 'HEALTHY' } | Select-Object -First 1
if (-not $activeWorker) {
    Write-Err "no ACTIVE+HEALTHY worker found"
    Write-Info "hint: 在 PC 端跑 scripts\install-go-and-run-worker.ps1 启动 worker"
    Write-Info "all workers: $($workers.records | ConvertTo-Json -Depth 3)"
    exit 1
}
$WORKER_ID = $activeWorker.id
Write-OK "worker #$WORKER_ID ACTIVE+HEALTHY, last heartbeat: $($activeWorker.lastHeartbeatAt)"

# ===== 3. 触发 deploy =====
Write-Step 3 "触发 deploy (POST /api/projects/$PROJECT_ID/deployments)"
$deployReq = @{
    envId       = $ENV_ID
    imageTag    = $IMAGE_TAG
    replicas    = 2
    triggeredBy = 'm9-e2e-test'
} | ConvertTo-Json
$deployResp = Invoke-Api POST "$SHIPYARD_URL/api/projects/$PROJECT_ID/deployments" $deployReq
$DEPLOY_ID = $deployResp.data.id
Write-OK "deploy #$DEPLOY_ID triggered, status=$($deployResp.data.status), namespace=$($deployResp.data.namespace)"
if ($deployResp.data.status -notin @('PENDING', 'RUNNING')) {
    Write-Err "unexpected status: $($deployResp.data.status), error=$($deployResp.data.errorMessage)"
    exit 1
}

# ===== 4. 等 deploy SUCCESS =====
Write-Step 4 "等 deploy SUCCESS (最多 $TIMEOUT_SEC 秒)"
$elapsed = 0
$deploy = $null
while ($elapsed -lt $TIMEOUT_SEC) {
    Start-Sleep -Seconds 3
    $elapsed += 3
    $deploy = (Invoke-Api GET "$SHIPYARD_URL/api/deployments/$DEPLOY_ID").data
    Write-Info "  [$elapsed s] deploy #$DEPLOY_ID status=$($deploy.status)"
    if ($deploy.status -in @('SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELED')) {
        break
    }
}
if ($deploy.status -ne 'SUCCESS') {
    Write-Err "deploy did not succeed: status=$($deploy.status), error=$($deploy.errorMessage)"
    exit 1
}
Write-OK "deploy #$DEPLOY_ID SUCCESS, finished at $($deploy.finishedAt)"

# ===== 5. k8s 真有 Deployment/Service =====
Write-Step 5 "kubectl 验证 k8s 真有 $DEPLOY_NAME 资源"
$ns_check = & $KUBECTL get namespace $NAMESPACE 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Err "namespace $NAMESPACE 不存在: $ns_check"
    exit 1
}
Write-OK "namespace $NAMESPACE 存在"

$deploy_k8s = & $KUBECTL get deployment $DEPLOY_NAME -n $NAMESPACE -o jsonpath='{.spec.replicas}' 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Err "kubectl get deployment 失败: $deploy_k8s"
    exit 1
}
Write-OK "Deployment/$DEPLOY_NAME 在 $NAMESPACE 存在, replicas=$deploy_k8s (期望 2)"
if ($deploy_k8s -ne '2') {
    Write-Err "replicas 期望 2, 实际 $deploy_k8s"
    exit 1
}

# ===== 6. snapshot 列表 =====
Write-Step 6 "列 snapshot (GET /api/deployments/$DEPLOY_ID/snapshots)"
$snapshots = (Invoke-Api GET "$SHIPYARD_URL/api/deployments/$DEPLOY_ID/snapshots").data
if ($snapshots.Count -lt 1) {
    Write-Err "snapshot 列表为空 (期望至少 1 个)"
    exit 1
}
$SNAPSHOT_ID = $snapshots[0].id
Write-OK "$($snapshots.Count) snapshot, 用 #$SNAPSHOT_ID 回滚"

# ===== 7. 触发回滚 =====
Write-Step 7 "触发回滚 (POST /api/deployments/$DEPLOY_ID/rollback/$SNAPSHOT_ID)"
$rollbackResp = Invoke-Api POST "$SHIPYARD_URL/api/deployments/$DEPLOY_ID/rollback/$SNAPSHOT_ID?triggeredBy=m9-e2e-rollback"
$ROLLBACK_ID = $rollbackResp.data.id
Write-OK "rollback deploy #$ROLLBACK_ID triggered, status=$($rollbackResp.data.status)"

# 等回滚 SUCCESS
$elapsed = 0
while ($elapsed -lt $TIMEOUT_SEC) {
    Start-Sleep -Seconds 3
    $elapsed += 3
    $rb = (Invoke-Api GET "$SHIPYARD_URL/api/deployments/$ROLLBACK_ID").data
    Write-Info "  [$elapsed s] rollback #$ROLLBACK_ID status=$($rb.status)"
    if ($rb.status -in @('SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELED')) {
        break
    }
}
if ($rb.status -ne 'SUCCESS') {
    Write-Err "rollback did not succeed: status=$($rb.status), error=$($rb.errorMessage)"
    exit 1
}
Write-OK "rollback SUCCESS"

# ===== 8. 验证回滚后 k8s 资源还在 (replicas 仍是 2) =====
Write-Step 8 "验证回滚后 k8s Deployment/$DEPLOY_NAME 仍存在 + replicas=2"
$deploy_k8s2 = & $KUBECTL get deployment $DEPLOY_NAME -n $NAMESPACE -o jsonpath='{.spec.replicas}' 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Err "kubectl get deployment (回滚后) 失败: $deploy_k8s2"
    exit 1
}
Write-OK "Deployment/$DEPLOY_NAME 仍存在, replicas=$deploy_k8s2 (回滚不丢资源)"

Write-Host "`n=== M9 端到端测试全部通过 ===" -ForegroundColor Green
Write-Host "deploy #$DEPLOY_ID SUCCESS, rollback #$ROLLBACK_ID SUCCESS, k8s 资源稳定" -ForegroundColor Green
