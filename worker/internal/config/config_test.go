package config

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestLoad_Defaults(t *testing.T) {
	// 清掉可能干扰的 env
	for _, k := range []string{
		"WORKER_PORT", "WORKER_ENV", "WORKER_NAME", "WORKER_VERSION",
		"SHIPYARD_URL", "K8S_IN_CLUSTER",
		"HEALTH_DISK_THRESHOLD_PERCENT", "HEALTH_MEM_MIN_AVAILABLE_MB",
		"HEALTH_K8S_CHECK_TIMEOUT_MS", "HEALTH_CACHE_TTL_SEC",
	} {
		os.Unsetenv(k)
	}

	cfg, err := Load()
	require.NoError(t, err)
	assert.Equal(t, 8888, cfg.Port)
	assert.Equal(t, "dev", cfg.Env)
	assert.NotEmpty(t, cfg.WorkerName)
	assert.Equal(t, "dev", cfg.Version)
	assert.Equal(t, "http://localhost:8080", cfg.ShipyardURL)
	assert.False(t, cfg.K8sInCluster)
	// M9 commit-9: health 默认值
	assert.Equal(t, 90, cfg.HealthDiskThresholdPercent)
	assert.Equal(t, 200, cfg.HealthMemMinAvailableMB)
	assert.Equal(t, 3000, cfg.HealthK8sCheckTimeoutMS)
	assert.Equal(t, 30, cfg.HealthCacheTTLSec)
}

func TestLoad_Override(t *testing.T) {
	os.Setenv("WORKER_PORT", "9999")
	os.Setenv("WORKER_ENV", "prod")
	os.Setenv("WORKER_NAME", "worker-prod-01")
	os.Setenv("WORKER_VERSION", "1.2.3")
	os.Setenv("SHIPYARD_URL", "http://shipyard:8080")
	os.Setenv("K8S_IN_CLUSTER", "true")
	defer func() {
		for _, k := range []string{"WORKER_PORT", "WORKER_ENV", "WORKER_NAME", "WORKER_VERSION", "SHIPYARD_URL", "K8S_IN_CLUSTER"} {
			os.Unsetenv(k)
		}
	}()

	cfg, err := Load()
	require.NoError(t, err)
	assert.Equal(t, 9999, cfg.Port)
	assert.Equal(t, "prod", cfg.Env)
	assert.Equal(t, "worker-prod-01", cfg.WorkerName)
	assert.Equal(t, "1.2.3", cfg.Version)
	assert.Equal(t, "http://shipyard:8080", cfg.ShipyardURL)
	assert.True(t, cfg.K8sInCluster)
}

func TestLoad_InvalidPort(t *testing.T) {
	// 99999 > 65535 应该 fail
	os.Setenv("WORKER_PORT", "99999")
	defer os.Unsetenv("WORKER_PORT")

	_, err := Load()
	assert.Error(t, err, "port > 65535 should fail")

	// "abc" 走 strconv fallback 返 8888,不报错
	os.Setenv("WORKER_PORT", "abc")
	cfg, err := Load()
	require.NoError(t, err)
	assert.Equal(t, 8888, cfg.Port, "non-numeric env falls back to default")
}
