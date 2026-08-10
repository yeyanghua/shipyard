// Package handler - health.go 健康检查 + echo 测试端点.
package handler

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"

	"github.com/yeyanghua/shipyard/worker/internal/types"
)

// HealthHandler 健康检查端点 (k8s liveness/readiness probe 用).
type HealthHandler struct {
	logger    *zap.Logger
	startTime time.Time
	version   string
}

// NewHealthHandler 创建 handler.
func NewHealthHandler(logger *zap.Logger, version string) *HealthHandler {
	return &HealthHandler{
		logger:    logger,
		startTime: time.Now(),
		version:   version,
	}
}

// Liveness GET /healthz — k8s liveness probe.
//
// 进程能响应就 OK (无依赖). 失败 → k8s 杀 pod 重启.
func (h *HealthHandler) Liveness(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"status": "ok",
		"uptime": time.Since(h.startTime).String(),
	})
}

// Readiness GET /readyz — k8s readiness probe.
//
// M8.1 只检查进程存活. M8.3 还要检查: client-go init 成功? shipyard URL 可达?
func (h *HealthHandler) Readiness(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"status":  "ok",
		"version": h.version,
		"uptime":  time.Since(h.startTime).String(),
	})
}

// EchoHandler M8.1 测试端点 — shipyard 调过来验证通信.
type EchoHandler struct {
	logger     *zap.Logger
	workerName string
	workerID   *string // 启动后从 shipyard 注册拿到 (shipyard 返 string)
}

// NewEchoHandler 创建 handler.
func NewEchoHandler(logger *zap.Logger, workerName string) *EchoHandler {
	return &EchoHandler{
		logger:     logger,
		workerName: workerName,
	}
}

// SetWorkerID 注册成功后调用.
func (h *EchoHandler) SetWorkerID(id string) {
	h.workerID = &id
}

// Echo POST /api/v1/tasks/echo — 收到 body 原样回,带 worker 标识.
func (h *EchoHandler) Echo(c *gin.Context) {
	var req types.EchoRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.Warn("echo: bad request", zap.Error(err))
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    400,
			"message": "invalid JSON body: " + err.Error(),
		})
		return
	}

	h.logger.Info("echo: received",
		zap.String("worker", h.workerName),
		zap.String("message", req.Message),
	)

	resp := types.EchoResponse{
		WorkerName:  h.workerName,
		Received:    req,
		ProcessedAt: time.Now(),
	}
	if h.workerID != nil {
		resp.WorkerID = *h.workerID
	}

	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "ok",
		"data":    resp,
	})
}
