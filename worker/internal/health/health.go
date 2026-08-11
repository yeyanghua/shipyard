// Package health - worker 自检逻辑 (M9 commit-9).
//
// 3 项检查:
//  1. k8s API 连通性 (调注入的 check fn, 默认 ListNamespaces, 带 timeout)
//  2. 内存 (从 /proc/meminfo 读 MemAvailable, 跟 threshold 比)
//  3. 磁盘 (从 / 读 statfs, percent = used / total)
//
// 任一项失败 → 返 UNHEALTHY + detail (失败原因).
// 全过 → 返 HEALTHY.
//
// 设计:
//   - threshold 走 config 注入, 默认值见 DefaultConfig
//   - k8sCheckFn 注入 (测试时 mock) — 默认逻辑是 k8sClient.ListNamespaces
//   - check 走 sync.Mutex 保护, cacheTTL 期间只跑 1 次 (避免 disk stat / meminfo 重复读)
package health

import (
	"context"
	"fmt"
	"os"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"go.uber.org/zap"
)

// CheckResult 自检结果.
type CheckResult struct {
	// HEALTHY / UNHEALTHY — 跟 shipyard WorkerHealthScanner 字段对齐.
	Health string

	// 失败原因 (HEALTHY 时为 "").
	// 例 "k8s API check failed: ..." / "mem available 100MB < 200MB" / "disk 95% >= 90%".
	Detail string
}

// IsHealthy 简写.
func (r CheckResult) IsHealthy() bool { return r.Health == "HEALTHY" }

// Config 自检器阈值配置.
type Config struct {
	DiskThresholdPercent int           // 磁盘使用率阈值 (默认 90, 超过 UNHEALTHY)
	MemMinAvailableMB    int           // 最小可用内存 MB (默认 200, 低于 UNHEALTHY)
	K8sCheckTimeout      time.Duration // k8s API 连通性检查超时 (默认 3s)
	CacheTTL             time.Duration // 缓存有效时间 (默认 30s, 避免重复 IO)
}

// DefaultConfig 默认阈值 — 跟 main.go 装配时 env var 覆盖.
func DefaultConfig() Config {
	return Config{
		DiskThresholdPercent: 90,
		MemMinAvailableMB:    200,
		K8sCheckTimeout:      3 * time.Second,
		CacheTTL:             30 * time.Second,
	}
}

// K8sCheckFn 注入的 k8s 连通性检查函数.
//
// 返 nil = 连通, err = 失败 (网络/超时/5xx). 4xx 业务错不算 health 失败, 应返 nil.
type K8sCheckFn func(ctx context.Context) error

// Checker 自检器.
//
// 装配: NewChecker(logger, cfg, k8sCheckFn).Check(ctx) 拿结果.
type Checker struct {
	logger *zap.Logger
	cfg    Config
	k8s    K8sCheckFn

	mu          sync.Mutex
	lastCheck   CheckResult
	lastCheckAt time.Time
}

// NewChecker 创建自检器.
func NewChecker(logger *zap.Logger, cfg Config, k8sCheckFn K8sCheckFn) *Checker {
	if k8sCheckFn == nil {
		// 兜底: nil fn 永远返 nil (k8s 检查永远过), 避免 nil panic.
		k8sCheckFn = func(ctx context.Context) error { return nil }
	}
	return &Checker{
		logger: logger,
		cfg:    cfg,
		k8s:    k8sCheckFn,
	}
}

// Check 跑全部 3 项自检, 返结果.
//
// CacheTTL 内重复调: 返 cache (避免 disk stat / meminfo 重复读).
func (c *Checker) Check(ctx context.Context) CheckResult {
	c.mu.Lock()
	defer c.mu.Unlock()

	// cache hit
	if c.lastCheck.Health != "" && time.Since(c.lastCheckAt) < c.cfg.CacheTTL {
		return c.lastCheck
	}

	// 1. k8s API 连通性
	k8sCtx, cancel := context.WithTimeout(ctx, c.cfg.K8sCheckTimeout)
	defer cancel()
	if err := c.k8s(k8sCtx); err != nil {
		result := CheckResult{
			Health: "UNHEALTHY",
			Detail: fmt.Sprintf("k8s API check failed: %v", err),
		}
		c.lastCheck = result
		c.lastCheckAt = time.Now()
		c.logger.Warn("Health check: k8s API 失败", zap.Error(err))
		return result
	}

	// 2. 内存检查
	if memResult := c.checkMemory(); !memResult.IsHealthy() {
		c.lastCheck = memResult
		c.lastCheckAt = time.Now()
		c.logger.Warn("Health check: 内存不足", zap.String("detail", memResult.Detail))
		return memResult
	}

	// 3. 磁盘检查
	if diskResult := c.checkDisk(); !diskResult.IsHealthy() {
		c.lastCheck = diskResult
		c.lastCheckAt = time.Now()
		c.logger.Warn("Health check: 磁盘满", zap.String("detail", diskResult.Detail))
		return diskResult
	}

	// 全过
	result := CheckResult{Health: "HEALTHY", Detail: ""}
	c.lastCheck = result
	c.lastCheckAt = time.Now()
	return result
}

// checkMemory 查 /proc/meminfo, 算 MemAvailable, 跟 threshold 比.
//
// 非 linux 系统 (Mac 开发) 读不到 /proc/meminfo → 返 HEALTHY 跳过 (避免开发态误报).
func (c *Checker) checkMemory() CheckResult {
	data, err := os.ReadFile("/proc/meminfo")
	if err != nil {
		return CheckResult{Health: "HEALTHY", Detail: ""}
	}

	memAvailKB, memTotalKB := parseMeminfo(string(data))
	if memAvailKB <= 0 || memTotalKB <= 0 {
		return CheckResult{Health: "HEALTHY", Detail: ""}
	}

	memAvailMB := memAvailKB / 1024
	if memAvailMB < int64(c.cfg.MemMinAvailableMB) {
		return CheckResult{
			Health: "UNHEALTHY",
			Detail: fmt.Sprintf("mem available %dMB < threshold %dMB",
				memAvailMB, c.cfg.MemMinAvailableMB),
		}
	}
	return CheckResult{Health: "HEALTHY", Detail: ""}
}

// checkDisk 查 / 的 statfs, 算 used/total, 跟 threshold 比.
//
// Mac 开发用 /tmp (避免 / 是 read-only 之类); linux 用 /.
func (c *Checker) checkDisk() CheckResult {
	fs := "/"
	if runtime.GOOS == "darwin" {
		fs = "/tmp"
	}

	var stat syscall.Statfs_t
	if err := syscall.Statfs(fs, &stat); err != nil {
		// 读不到 (sandbox/容器没权限) → 跳过, 返 HEALTHY.
		return CheckResult{Health: "HEALTHY", Detail: ""}
	}

	// Blocks 是 fs block count, Bsize 是 block size (bytes)
	totalBytes := stat.Blocks * uint64(stat.Bsize)
	// Bavail 是 non-root 可用 blocks
	freeBytes := stat.Bavail * uint64(stat.Bsize)
	if totalBytes == 0 {
		return CheckResult{Health: "HEALTHY", Detail: ""}
	}
	usedBytes := totalBytes - freeBytes
	if usedBytes > totalBytes {
		// statfs 在某些 fs 上 Bavail > Blocks (reserved blocks 算在 Blocks 里), 保护一下
		usedBytes = totalBytes
	}

	usedPercent := int(usedBytes * 100 / totalBytes)
	if usedPercent >= c.cfg.DiskThresholdPercent {
		return CheckResult{
			Health: "UNHEALTHY",
			Detail: fmt.Sprintf("disk %d%% >= threshold %d%% (path=%s)",
				usedPercent, c.cfg.DiskThresholdPercent, fs),
		}
	}
	return CheckResult{Health: "HEALTHY", Detail: ""}
}

// parseMeminfo 解析 /proc/meminfo 找 MemAvailable + MemTotal.
//
// 例:
//
//	MemTotal:       16384000 kB
//	MemAvailable:    8192000 kB
//
// 非 linux 系统 (读不到) 返 0, 0.
func parseMeminfo(content string) (availKB, totalKB int64) {
	for _, line := range strings.Split(content, "\n") {
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		key := strings.TrimSuffix(fields[0], ":")
		val, err := strconv.ParseInt(fields[1], 10, 64)
		if err != nil {
			continue
		}
		switch key {
		case "MemTotal":
			totalKB = val
		case "MemAvailable":
			availKB = val
		}
	}
	return
}
