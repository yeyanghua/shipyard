// Package server gin HTTP server 装配.
package server

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"

	"github.com/yeyanghua/shipyard/worker/internal/config"
	"github.com/yeyanghua/shipyard/worker/internal/handler"
)

// Server HTTP server 封装.
type Server struct {
	cfg    *config.Config
	logger *zap.Logger
	engine *gin.Engine
	srv    *http.Server
}

// New 创建 server 实例,装配所有 handler.
func New(
	cfg *config.Config,
	logger *zap.Logger,
	cluster *handler.ClusterHandler,
	health *handler.HealthHandler,
	echo *handler.EchoHandler,
	deploy *handler.DeployHandler,
) *Server {
	if cfg.Env != "dev" {
		gin.SetMode(gin.ReleaseMode)
	}

	engine := gin.New()
	engine.Use(gin.Recovery()) // panic 兜底
	engine.Use(requestLogger(logger))

	// === 健康检查 (根路径, 方便 k8s probe) ===
	engine.GET("/healthz", health.Liveness)
	engine.GET("/readyz", health.Readiness)

	// === V1 API ===
	v1 := engine.Group("/api/v1")
	{
		// 测试用 echo
		v1.POST("/tasks/echo", echo.Echo)

		// 集群读类 (M8.1 mock, M8.3 切真 client-go)
		clusterGroup := v1.Group("/cluster")
		{
			clusterGroup.GET("/namespaces", cluster.ListNamespaces)
			clusterGroup.GET("/pods", cluster.ListPods)
			clusterGroup.GET("/deployments", cluster.ListDeployments)
			// M9 commit-16: worker 自己 deployment 的 pod 列表 (replicas + pod 状态)
			// shipyard 用这个展示 "1 worker DB row 对应 N 个 k8s pod"
			clusterGroup.GET("/worker-pods", cluster.ListWorkerPods)
		}

		// 写动作 (M9 commit-8): shipyard → worker 真 apply / scale / manifest
		taskGroup := v1.Group("/tasks")
		{
			taskGroup.POST("/deploy", deploy.Deploy)
			taskGroup.POST("/scale", deploy.Scale)
			taskGroup.GET("/manifest", deploy.GetManifest)
		}
	}

	srv := &http.Server{
		Addr:              fmt.Sprintf(":%d", cfg.Port),
		Handler:           engine,
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       120 * time.Second,
	}

	return &Server{
		cfg:    cfg,
		logger: logger,
		engine: engine,
		srv:    srv,
	}
}

// Start 启动 HTTP server (阻塞直到 Shutdown).
func (s *Server) Start() error {
	s.logger.Info("HTTP server starting",
		zap.Int("port", s.cfg.Port),
		zap.String("env", s.cfg.Env),
	)
	if err := s.srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		return fmt.Errorf("HTTP server failed: %w", err)
	}
	return nil
}

// Shutdown 优雅关闭.
func (s *Server) Shutdown(ctx context.Context) error {
	return s.srv.Shutdown(ctx)
}

// Engine 返回 gin engine (测试用).
func (s *Server) Engine() *gin.Engine {
	return s.engine
}

// requestLogger access log 中间件.
func requestLogger(logger *zap.Logger) gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		c.Next()
		latency := time.Since(start)

		// 5xx 才打 warn,其他 info
		fields := []zap.Field{
			zap.String("method", c.Request.Method),
			zap.String("path", c.Request.URL.Path),
			zap.Int("status", c.Writer.Status()),
			zap.Duration("latency", latency),
			zap.String("ip", c.ClientIP()),
		}

		if c.Writer.Status() >= 500 {
			logger.Error("request", fields...)
		} else {
			logger.Info("request", fields...)
		}
	}
}
