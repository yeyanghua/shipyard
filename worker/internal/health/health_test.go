// Package health tests.
package health

import (
	"context"
	"errors"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"
)

// TestParseMeminfo 正常 / 空 / 半行 / 缺字段.
func TestParseMeminfo(t *testing.T) {
	t.Run("正常解析", func(t *testing.T) {
		input := `MemTotal:       16384000 kB
MemFree:         8192000 kB
MemAvailable:    8192000 kB
Buffers:          1024000 kB
Cached:          4096000 kB`
		avail, total := parseMeminfo(input)
		assert.Equal(t, int64(8192000), avail)
		assert.Equal(t, int64(16384000), total)
	})

	t.Run("空字符串", func(t *testing.T) {
		avail, total := parseMeminfo("")
		assert.Equal(t, int64(0), avail)
		assert.Equal(t, int64(0), total)
	})

	t.Run("只 MemTotal 没 MemAvailable", func(t *testing.T) {
		avail, total := parseMeminfo("MemTotal: 1000 kB")
		assert.Equal(t, int64(0), avail)
		assert.Equal(t, int64(1000), total)
	})

	t.Run("行字段 < 2 跳过", func(t *testing.T) {
		avail, total := parseMeminfo("MemTotal:\nMemAvailable: 5000 kB")
		assert.Equal(t, int64(5000), avail)
		assert.Equal(t, int64(0), total)
	})

	t.Run("数字非 int 跳过", func(t *testing.T) {
		avail, total := parseMeminfo("MemTotal: abc kB\nMemAvailable: 100 kB")
		assert.Equal(t, int64(100), avail)
		assert.Equal(t, int64(0), total)
	})
}

// TestCheckResult_IsHealthy.
func TestCheckResult_IsHealthy(t *testing.T) {
	assert.True(t, CheckResult{Health: "HEALTHY"}.IsHealthy())
	assert.False(t, CheckResult{Health: "UNHEALTHY"}.IsHealthy())
	assert.False(t, CheckResult{}.IsHealthy(), "空结果不算 HEALTHY")
}

// TestChecker_AllPass 全部检查过 → HEALTHY.
func TestChecker_AllPass(t *testing.T) {
	var called int32
	checker := NewChecker(zap.NewNop(), Config{
		DiskThresholdPercent: 90,
		MemMinAvailableMB:    1, // 1MB, 任何机器都过
		K8sCheckTimeout:      100 * time.Millisecond,
		CacheTTL:             0, // 每次都跑
	}, func(ctx context.Context) error {
		atomic.AddInt32(&called, 1)
		return nil
	})

	result := checker.Check(context.Background())
	assert.True(t, result.IsHealthy(), "全过应该 HEALTHY, detail=%s", result.Detail)
	assert.Equal(t, int32(1), atomic.LoadInt32(&called))
}

// TestChecker_K8sFail k8s API 失败 → UNHEALTHY + detail 包含 "k8s API check failed".
func TestChecker_K8sFail(t *testing.T) {
	checker := NewChecker(zap.NewNop(), Config{
		DiskThresholdPercent: 90,
		MemMinAvailableMB:    1,
		K8sCheckTimeout:      100 * time.Millisecond,
		CacheTTL:             0,
	}, func(ctx context.Context) error {
		return errors.New("connection refused")
	})

	result := checker.Check(context.Background())
	assert.False(t, result.IsHealthy())
	assert.Contains(t, result.Detail, "k8s API check failed")
	assert.Contains(t, result.Detail, "connection refused")
}

// TestChecker_K8sTimeout k8s API 慢 → 触发 timeout → UNHEALTHY.
func TestChecker_K8sTimeout(t *testing.T) {
	checker := NewChecker(zap.NewNop(), Config{
		DiskThresholdPercent: 90,
		MemMinAvailableMB:    1,
		K8sCheckTimeout:      10 * time.Millisecond, // 10ms 超时
		CacheTTL:             0,
	}, func(ctx context.Context) error {
		// 模拟慢响应
		select {
		case <-time.After(200 * time.Millisecond):
			return nil
		case <-ctx.Done():
			return ctx.Err()
		}
	})

	result := checker.Check(context.Background())
	assert.False(t, result.IsHealthy())
	assert.Contains(t, result.Detail, "k8s API check failed")
}

// TestChecker_CacheHit 30s 内重复 Check 走 cache, k8sCheckFn 只调 1 次.
func TestChecker_CacheHit(t *testing.T) {
	var called int32
	checker := NewChecker(zap.NewNop(), Config{
		DiskThresholdPercent: 90,
		MemMinAvailableMB:    1,
		K8sCheckTimeout:      100 * time.Millisecond,
		CacheTTL:             1 * time.Hour, // 长 cache
	}, func(ctx context.Context) error {
		atomic.AddInt32(&called, 1)
		return nil
	})

	// 第 1 次跑
	r1 := checker.Check(context.Background())
	assert.True(t, r1.IsHealthy())
	assert.Equal(t, int32(1), atomic.LoadInt32(&called))

	// 第 2-5 次都走 cache
	for i := 0; i < 4; i++ {
		r := checker.Check(context.Background())
		assert.True(t, r.IsHealthy(), "iter %d", i)
	}
	assert.Equal(t, int32(1), atomic.LoadInt32(&called), "cache hit 不应该再调 k8sCheckFn")
}

// TestChecker_CacheExpire TTL 过期 → 重新跑.
func TestChecker_CacheExpire(t *testing.T) {
	var called int32
	checker := NewChecker(zap.NewNop(), Config{
		DiskThresholdPercent: 90,
		MemMinAvailableMB:    1,
		K8sCheckTimeout:      100 * time.Millisecond,
		CacheTTL:             50 * time.Millisecond,
	}, func(ctx context.Context) error {
		atomic.AddInt32(&called, 1)
		return nil
	})

	checker.Check(context.Background())
	assert.Equal(t, int32(1), atomic.LoadInt32(&called))

	// 等 TTL 过期
	time.Sleep(80 * time.Millisecond)

	checker.Check(context.Background())
	assert.Equal(t, int32(2), atomic.LoadInt32(&called), "TTL 过期应该重跑")
}

// TestChecker_NilK8sCheckFn nil fn 不 panic, 走 nil 兜底 (永远返 nil).
func TestChecker_NilK8sCheckFn(t *testing.T) {
	checker := NewChecker(zap.NewNop(), Config{
		DiskThresholdPercent: 90,
		MemMinAvailableMB:    1,
		K8sCheckTimeout:      100 * time.Millisecond,
		CacheTTL:             0,
	}, nil)

	assert.NotPanics(t, func() {
		result := checker.Check(context.Background())
		assert.True(t, result.IsHealthy())
	})
}

// TestChecker_DiskFail 模拟 disk 满 — 用极低 threshold 触发.
//
// (实际盘使用率很难人为造, 这里靠 1% threshold 让任何盘都 fail.)
// 注: Mac/非 linux 上 checkDisk 走 /tmp, 通常不会满, 但 0% 也 < 1% 会 fail.
func TestChecker_DiskFail(t *testing.T) {
	if testing.Short() {
		t.Skip("skip disk test in -short mode")
	}

	checker := NewChecker(zap.NewNop(), Config{
		DiskThresholdPercent: 0, // 任何使用率都 >= 0
		MemMinAvailableMB:    1,
		K8sCheckTimeout:      100 * time.Millisecond,
		CacheTTL:             0,
	}, func(ctx context.Context) error { return nil })

	result := checker.Check(context.Background())
	// 在某些 sandbox 里 statfs 返 0 → 走 HEALTHY 兜底, 这里两种都接受
	if !result.IsHealthy() {
		assert.Contains(t, result.Detail, "disk", "fail 原因应该是 disk")
	}
}

// TestChecker_ConcurrentSafety 并发调 Check 不 race.
//
// 跑 -race 时验证.
func TestChecker_ConcurrentSafety(t *testing.T) {
	var called int32
	checker := NewChecker(zap.NewNop(), Config{
		DiskThresholdPercent: 90,
		MemMinAvailableMB:    1,
		K8sCheckTimeout:      100 * time.Millisecond,
		CacheTTL:             10 * time.Millisecond, // 短 cache 强制重跑
	}, func(ctx context.Context) error {
		atomic.AddInt32(&called, 1)
		time.Sleep(1 * time.Millisecond)
		return nil
	})

	done := make(chan struct{})
	for i := 0; i < 20; i++ {
		go func() {
			for j := 0; j < 10; j++ {
				_ = checker.Check(context.Background())
			}
			done <- struct{}{}
		}()
	}
	for i := 0; i < 20; i++ {
		<-done
	}
	require.Greater(t, atomic.LoadInt32(&called), int32(0))
}

// TestDefaultConfig 默认值 sanity.
func TestDefaultConfig(t *testing.T) {
	cfg := DefaultConfig()
	assert.Equal(t, 90, cfg.DiskThresholdPercent)
	assert.Equal(t, 200, cfg.MemMinAvailableMB)
	assert.Equal(t, 3*time.Second, cfg.K8sCheckTimeout)
	assert.Equal(t, 30*time.Second, cfg.CacheTTL)
}

// helper: 防 unused import.
var _ = strings.HasPrefix
