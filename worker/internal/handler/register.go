// Package handler - register.go worker 主动注册到 shipyard + 心跳.
package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"sync"
	"time"

	"go.uber.org/zap"

	"github.com/yeyanghua/shipyard/worker/internal/types"
)

// RegisterHandler 负责 worker 主动注册到 shipyard,并定期发心跳.
type RegisterHandler struct {
	logger      *zap.Logger
	cfg         RegisterConfig
	mu          sync.RWMutex
	workerID    string
	registered  bool
	echoHandler *EchoHandler // 拿到 ID 后回填
	httpClient  *http.Client
	stopCh      chan struct{}
}

// RegisterConfig 注册/心跳配置.
type RegisterConfig struct {
	ShipyardURL       string
	WorkerName        string
	Env               string
	K8sVersion        string
	NodeName          string
	WorkerURL         string
	WorkerToken       string
	Version           string
	HeartbeatInterval time.Duration

	// M9 commit-9: 心跳里带的 health 字段由外部 (main.go) 注入 checker 决定.
	// 不在 register.go 内部跑 health check, 保持 handler 薄.
	HealthFn func(ctx context.Context) types.HealthStatus
}

// NewRegisterHandler 创建 handler (不自动启动,等 worker main 调 Start).
func NewRegisterHandler(
	logger *zap.Logger,
	cfg RegisterConfig,
	echoHandler *EchoHandler,
) *RegisterHandler {
	return &RegisterHandler{
		logger:      logger,
		cfg:         cfg,
		echoHandler: echoHandler,
		httpClient:  &http.Client{Timeout: 10 * time.Second},
		stopCh:      make(chan struct{}),
	}
}

// WorkerID 拿到 shipyard 分配的 worker ID.
//
// 返回 string: shipyard Jackson 把雪花 ID (Long) 序列化成 String 防 JS 19 位精度丢失,
// worker 端统一用 string 接,调 path / log 时无需再转.
func (r *RegisterHandler) WorkerID() string {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.workerID
}

// IsRegistered 是否已注册成功.
func (r *RegisterHandler) IsRegistered() bool {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.registered
}

// Start 启动注册流程: 先注册,成功后开心跳 goroutine.
//
// 返回 error: 如果首次注册失败(后续心跳内部重试).
func (r *RegisterHandler) Start(ctx context.Context) error {
	if err := r.registerOnce(ctx); err != nil {
		r.logger.Warn("initial register failed, will retry on heartbeat",
			zap.Error(err),
		)
	}

	// 启动心跳 loop
	go r.heartbeatLoop(ctx)
	return nil
}

// Stop 停心跳 loop.
func (r *RegisterHandler) Stop() {
	close(r.stopCh)
}

// registerOnce 调 shipyard POST /api/workers/register 拿 worker ID.
func (r *RegisterHandler) registerOnce(ctx context.Context) error {
	req := types.RegisterRequest{
		WorkerName:  r.cfg.WorkerName,
		Env:         r.cfg.Env,
		K8sVersion:  r.cfg.K8sVersion,
		NodeName:    r.cfg.NodeName,
		WorkerURL:   r.cfg.WorkerURL,
		WorkerToken: r.cfg.WorkerToken,
		Version:     r.cfg.Version,
	}

	body, _ := json.Marshal(req)
	url := r.cfg.ShipyardURL + "/api/workers/register"
	httpReq, _ := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	httpReq.Header.Set("Content-Type", "application/json")

	resp, err := r.httpClient.Do(httpReq)
	if err != nil {
		return fmt.Errorf("register HTTP call failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("register returned status %d", resp.StatusCode)
	}

	// 解析 shipyard 的 {code, message, data: RegisterResponse}
	var envelope struct {
		Code    int                    `json:"code"`
		Message string                 `json:"message"`
		Data    types.RegisterResponse `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&envelope); err != nil {
		return fmt.Errorf("decode register response: %w", err)
	}
	if envelope.Code != 0 {
		return fmt.Errorf("register business error: code=%d message=%s", envelope.Code, envelope.Message)
	}

	r.mu.Lock()
	r.workerID = envelope.Data.WorkerID
	r.registered = true
	r.mu.Unlock()

	// 回填 echo handler
	r.echoHandler.SetWorkerID(envelope.Data.WorkerID)

	r.logger.Info("registered to shipyard",
		zap.String("worker", r.cfg.WorkerName),
		zap.String("workerId", envelope.Data.WorkerID),
		zap.String("shipyard", r.cfg.ShipyardURL),
	)
	return nil
}

// heartbeatLoop 定期调 /api/workers/{id}/heartbeat.
func (r *RegisterHandler) heartbeatLoop(ctx context.Context) {
	ticker := time.NewTicker(r.cfg.HeartbeatInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-r.stopCh:
			return
		case <-ticker.C:
			r.sendHeartbeat(ctx)
		}
	}
}

func (r *RegisterHandler) sendHeartbeat(ctx context.Context) {
	r.mu.RLock()
	id := r.workerID
	registered := r.registered
	r.mu.RUnlock()

	// 还没注册成功,先尝试注册
	if !registered {
		if err := r.registerOnce(ctx); err != nil {
			r.logger.Warn("heartbeat-time register retry failed", zap.Error(err))
		}
		return
	}

	req := types.HeartbeatRequest{
		WorkerID: id,
		Status:   "online",
	}

	// M9 commit-9: 拿 health result 填到心跳, shipyard 端 WorkerHealthScanner 拿这个判 HEALTHY/UNHEALTHY.
	if r.cfg.HealthFn != nil {
		hs := r.cfg.HealthFn(ctx)
		req.Health = hs.Health
		req.HealthDetail = hs.Detail
	}

	body, _ := json.Marshal(req)
	url := fmt.Sprintf("%s/api/workers/%s/heartbeat", r.cfg.ShipyardURL, id)
	httpReq, _ := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	httpReq.Header.Set("Content-Type", "application/json")

	resp, err := r.httpClient.Do(httpReq)
	if err != nil {
		r.logger.Warn("heartbeat failed", zap.Error(err))
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		r.logger.Warn("heartbeat non-200",
			zap.Int("status", resp.StatusCode),
		)
	}
}
