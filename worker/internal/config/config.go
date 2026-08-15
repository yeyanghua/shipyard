// Package config 从 env var 读 worker 启动配置.
package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

// Config worker 启动配置.
type Config struct {
	Port              int           // HTTP 监听端口
	Env               string        // 环境名 dev/test/prod
	WorkerName        string        // worker 唯一名
	Version           string        // worker 版本 (从 ldflags 注入)
	ShipyardURL       string        // shipyard 后端地址
	HeartbeatInterval time.Duration // 心跳间隔
	K8sInCluster      bool          // true = in-cluster;false = 走 KUBECONFIG
	KubeConfigPath    string        // K8sInCluster=false 时读这个

	// M9 commit-9: health 自检配置.
	HealthDiskThresholdPercent int           // 磁盘使用率阈值 (默认 90)
	HealthMemMinAvailableMB    int           // 最小可用内存 MB (默认 200)
	HealthK8sCheckTimeoutMS    int           // k8s 连通检查超时 ms (默认 3000)
	HealthCacheTTLSec          int           // 自检 cache TTL 秒 (默认 30)
}

// Load 从 env var 加载,带默认值.
func Load() (*Config, error) {
	cfg := &Config{
		Port:                       getEnvInt("WORKER_PORT", 8888),
		Env:                        getEnvStr("WORKER_ENV", "dev"),
		WorkerName:                 getEnvStr("WORKER_NAME", fmt.Sprintf("worker-%s", getHostname())),
		Version:                    getEnvStr("WORKER_VERSION", "dev"),
		ShipyardURL:                getEnvStr("SHIPYARD_URL", "http://localhost:8080"),
		HeartbeatInterval:          time.Duration(getEnvInt("WORKER_HEARTBEAT_INTERVAL_SEC", 30)) * time.Second,
		K8sInCluster:               getEnvBool("K8S_IN_CLUSTER", false),
		KubeConfigPath:             getEnvStr("KUBECONFIG", ""),
		HealthDiskThresholdPercent: getEnvInt("HEALTH_DISK_THRESHOLD_PERCENT", 90),
		HealthMemMinAvailableMB:    getEnvInt("HEALTH_MEM_MIN_AVAILABLE_MB", 200),
		HealthK8sCheckTimeoutMS:    getEnvInt("HEALTH_K8S_CHECK_TIMEOUT_MS", 3000),
		HealthCacheTTLSec:          getEnvInt("HEALTH_CACHE_TTL_SEC", 30),
	}

	if cfg.Port < 1 || cfg.Port > 65535 {
		return nil, fmt.Errorf("invalid WORKER_PORT: %d", cfg.Port)
	}
	if cfg.Env == "" {
		return nil, fmt.Errorf("WORKER_ENV cannot be empty")
	}
	// M9.5: WorkerName 改 optional (UI 创建 worker 时填, worker 端 register 不再必填)
	if cfg.WorkerName == "" {
		logger_placeholder("WORKER_NAME 未设, 走 dev fallback (建议生产用 shipyard UI 创建 worker 时填)")
		cfg.WorkerName = fmt.Sprintf("worker-%s", getHostname())
	}

	return cfg, nil
}

// logger_placeholder 避免 main.go logger 未初始化时 worker config.Load() 静默失败
// 真实日志在 main.go 里用 logger.Warn 重新打
func logger_placeholder(msg string) {
	fmt.Fprintf(os.Stderr, "[worker config] %s\n", msg)
}

func getEnvStr(key, def string) string {
	if v, ok := os.LookupEnv(key); ok {
		return v
	}
	return def
}

func getEnvInt(key string, def int) int {
	if v, ok := os.LookupEnv(key); ok {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}

func getEnvBool(key string, def bool) bool {
	if v, ok := os.LookupEnv(key); ok {
		if b, err := strconv.ParseBool(v); err == nil {
			return b
		}
	}
	return def
}

func getHostname() string {
	h, err := os.Hostname()
	if err != nil {
		return "unknown"
	}
	return h
}
