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
}

// Load 从 env var 加载,带默认值.
func Load() (*Config, error) {
	cfg := &Config{
		Port:              getEnvInt("WORKER_PORT", 8888),
		Env:               getEnvStr("WORKER_ENV", "dev"),
		WorkerName:        getEnvStr("WORKER_NAME", fmt.Sprintf("worker-%s", getHostname())),
		Version:           getEnvStr("WORKER_VERSION", "dev"),
		ShipyardURL:       getEnvStr("SHIPYARD_URL", "http://localhost:8080"),
		HeartbeatInterval: time.Duration(getEnvInt("WORKER_HEARTBEAT_INTERVAL_SEC", 30)) * time.Second,
		K8sInCluster:      getEnvBool("K8S_IN_CLUSTER", false),
		KubeConfigPath:    getEnvStr("KUBECONFIG", ""),
	}

	if cfg.Port < 1 || cfg.Port > 65535 {
		return nil, fmt.Errorf("invalid WORKER_PORT: %d", cfg.Port)
	}
	if cfg.Env == "" {
		return nil, fmt.Errorf("WORKER_ENV cannot be empty")
	}
	if cfg.WorkerName == "" {
		return nil, fmt.Errorf("WORKER_NAME cannot be empty")
	}

	return cfg, nil
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
