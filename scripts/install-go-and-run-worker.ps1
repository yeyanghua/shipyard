# install-go-and-run-worker.ps1
# shipyard worker 一键装 Go + 跑 worker (PC 端用)
#
# 做什么:
#   1. 检查 Go 装没装 + 版本
#   2. 没装 / 太低 → 下载 1.23.4 zip (国内镜像) → 解压到 %USERPROFILE%\go123 → 加 PATH
#   3. 装 / 升级了 → 配 GOPROXY 镜像 (goproxy.cn, 避免拉依赖超时)
#   4. cd 到 worker 目录, go mod tidy
#   5. 设置 KUBECONFIG (家里集群) + WORKER_NAME + WORKER_ENV
#   6. go run ./cmd/worker (前台, Ctrl+C 停)
#
# 怎么用 (PC 端):
#   1. cd D:\Projects\shipyard
#   2. git pull origin main
#   3. powershell -ExecutionPolicy Bypass -File scripts\install-go-and-run-worker.ps1
#
# 不需要管理员权限 (解压到用户目录)
# 不会污染系统 PATH (只对当前 PowerShell 进程有效)
# Go 装在 %USERPROFILE%\go123\ (zip 解压, 不是 MSI)

$ErrorActionPreference = 'Stop'

# === 配置 (可改) ===
$GO_VERSION = '1.23.4'
$GO_DIR = Join-Path $env:USERPROFILE 'go123'
$GO_ZIP = Join-Path $env:USERPROFILE 'go123.zip'
$GO_URL = "https://mirrors.aliyun.com/golang/go$GO_VERSION.windows-amd64.zip"  # 国内镜像
$GOPROXY_CN = 'https://goproxy.cn,direct'

# worker 配置
$WORKER_NAME_DEFAULT = 'pc-worker-1'
$WORKER_ENV_DEFAULT = 'dev'
$WORKER_PORT_DEFAULT = 8888
$KUBECONFIG_DEFAULT = Join-Path $env:USERPROFILE '.kube\config'

# 仓库根
$REPO_ROOT = Split-Path -Parent $PSScriptRoot
$WORKER_DIR = Join-Path $REPO_ROOT 'worker'

# === 颜色 (控制台) ===
function Write-Step($msg) { Write-Host '==>' $msg -ForegroundColor Cyan }
function Write-OK($msg)   { Write-Host ' ✓ ' $msg -ForegroundColor Green }
function Write-Warn($msg) { Write-Host ' ! ' $msg -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host ' ✗ ' $msg -ForegroundColor Red }

# === 0. 引导 (worker 跑在用户 PC 上, 集群连家里 192.168.91.138) ===
Write-Host ''
Write-Host '============================================' -ForegroundColor Magenta
Write-Host '  shipyard worker 一键安装 + 启动           ' -ForegroundColor Magenta
Write-Host '  Go 1.23.4 + 家里真集群 (kubeconfig 模式)' -ForegroundColor Magenta
Write-Host '============================================' -ForegroundColor Magenta
Write-Host ''
Write-Host "  Go 装在:        $GO_DIR"
Write-Host "  worker 目录:    $WORKER_DIR"
Write-Host "  KUBECONFIG:     $KUBECONFIG_DEFAULT (默认, 可改)"
Write-Host "  WORKER_NAME:    $WORKER_NAME_DEFAULT (默认, 可改)"
Write-Host ''

# === 1. 探查现有 Go ===
$currentGo = $null
try {
    $currentGo = & go version 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-OK "Go 已装: $currentGo"
    } else {
        $currentGo = $null
    }
} catch {
    $currentGo = $null
}

$needInstall = $true
if ($currentGo) {
    # 解析版本号 (e.g. "go version go1.23.4 windows/amd64" → "1.23.4")
    $ver = ($currentGo -split ' ')[2] -replace '^go', ''
    $verMajor, $verMinor = $ver.Split('.')[0..1]
    $verNum = [int]"$verMajor$verMinor"

    if ($verNum -ge 123) {
        Write-OK "Go 版本 $ver >= 1.23, 跳过安装"
        $needInstall = $false
    } else {
        Write-Warn "Go 版本 $ver < 1.23, 需要升级"
    }
}

# === 2. 装 / 升 Go ===
if ($needInstall) {
    Write-Step "下载 Go $GO_VERSION (国内镜像)..."

    if (Test-Path $GO_ZIP) {
        Write-OK "已有 $GO_ZIP, 跳过下载"
    } else {
        Write-Host "  URL: $GO_URL"
        try {
            Invoke-WebRequest -Uri $GO_URL -OutFile $GO_ZIP -UseBasicParsing
        } catch {
            # fallback go.dev 官方
            $fallback = "https://go.dev/dl/go$GO_VERSION.windows-amd64.zip"
            Write-Warn "国内镜像失败, 切官方: $fallback"
            Invoke-WebRequest -Uri $fallback -OutFile $GO_ZIP -UseBasicParsing
        }
        $size = [math]::Round((Get-Item $GO_ZIP).Length / 1MB, 1)
        Write-OK "下载完成 ($size MB)"
    }

    if (-not (Test-Path (Join-Path $GO_DIR 'bin\go.exe'))) {
        Write-Step "解压到 $GO_DIR ..."
        Expand-Archive -Path $GO_ZIP -DestinationPath $GO_DIR -Force
        Write-OK "解压完成"
    } else {
        Write-OK "$GO_DIR 已有 go.exe, 跳过解压"
    }

    Write-Warn "zip 包保留在 $GO_ZIP (手动删除可省 200MB), 不影响使用"
}

# === 3. 加 PATH (当前进程) ===
$goBin = Join-Path $GO_DIR 'bin'
$env:PATH = "$goBin;$env:PATH"
Write-OK "PATH 已加 $goBin (本进程有效)"

# === 4. 验证 ===
$goVer = & go version
Write-OK "Go: $goVer"

# === 5. GOPROXY 镜像 (拉依赖加速) ===
$env:GOPROXY = $GOPROXY_CN
Write-OK "GOPROXY = $GOPROXY_CN"

# === 6. 检查 KUBECONFIG ===
$useKubeconfig = $env:KUBECONFIG
if (-not $useKubeconfig) {
    if (Test-Path $KUBECONFIG_DEFAULT) {
        $env:KUBECONFIG = $KUBECONFIG_DEFAULT
        Write-OK "KUBECONFIG = $KUBECONFIG_DEFAULT"
    } else {
        Write-Warn "KUBECONFIG 默认路径不存在: $KUBECONFIG_DEFAULT"
        Write-Warn "如果 worker 走 in-cluster 模式 (k8s 部署), 这个无所谓"
        Write-Warn "如果走 kubeconfig 模式, 需要先生成 kubeconfig 或改环境变量"
    }
} else {
    Write-OK "KUBECONFIG (env 已有): $useKubeconfig"
}

# === 7. 询问 / 应用 worker 配置 ===
if (-not $env:WORKER_NAME) { $env:WORKER_NAME = $WORKER_NAME_DEFAULT }
if (-not $env:WORKER_ENV)  { $env:WORKER_ENV  = $WORKER_ENV_DEFAULT }
if (-not $env:WORKER_PORT) { $env:WORKER_PORT = $WORKER_PORT_DEFAULT }

Write-Host ''
Write-Step "最终配置:"
Write-Host "  WORKER_NAME = $env:WORKER_NAME"
Write-Host "  WORKER_ENV  = $env:WORKER_ENV"
Write-Host "  WORKER_PORT = $env:WORKER_PORT"
Write-Host "  KUBECONFIG  = $env:KUBECONFIG"
Write-Host "  GOPROXY     = $env:GOPROXY"
Write-Host ''

# === 8. go mod tidy ===
Write-Step "go mod tidy (拉依赖) ..."
Push-Location $WORKER_DIR
try {
    & go mod tidy 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "go mod tidy 失败, 看上面错误"
    }
    Write-OK "go mod tidy 完成"
} finally {
    Pop-Location
}

# === 9. 启动 worker ===
Write-Step "启动 worker (前台, Ctrl+C 停) ..."
Write-Host ''
Write-Host '  健康检查: curl http://localhost:8888/healthz' -ForegroundColor Gray
Write-Host '  列 namespaces: curl http://localhost:8888/api/v1/cluster/namespaces' -ForegroundColor Gray
Write-Host '  注册到 shipyard: curl -X POST http://localhost:8080/api/workers/register ...' -ForegroundColor Gray
Write-Host ''

Push-Location $WORKER_DIR
& go run ./cmd/worker
Pop-Location
