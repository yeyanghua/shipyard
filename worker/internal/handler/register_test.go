package handler

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	"github.com/yeyanghua/shipyard/worker/internal/types"
)

// 注: 本文件测试 register handler 跟 echo 的 workerID 注入.
// httptest 起 mock shipyard server,模拟 register + heartbeat 流程.

func TestRegisterHandler_Start_RetryWhenShipyardDown(t *testing.T) {
	// shipyard 不在,Start 不应该 panic,内部失败转 warn 日志 + 心跳重试
	echo := NewEchoHandler(zap.NewNop(), "test-worker")
	reg := NewRegisterHandler(zap.NewNop(), RegisterConfig{
		ShipyardURL:       "http://127.0.0.1:1", // 故意连不上
		WorkerName:        "test-worker",
		Env:               "test",
		HeartbeatInterval: 100 * time.Millisecond,
	}, echo)

	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()

	// Start 不返 error (设计:失败转后台重试)
	err := reg.Start(ctx)
	assert.NoError(t, err, "Start should not return error even when shipyard down")

	// 状态: 还没注册成功
	assert.False(t, reg.IsRegistered(), "should not be registered")
	assert.Equal(t, "", reg.WorkerID(), "worker ID should be empty string")

	reg.Stop()
}

func TestRegisterHandler_Start_RegisterAndHeartbeat(t *testing.T) {
	// mock shipyard: 收 register + heartbeat
	var registerCalls int32
	var heartbeatCalls int32
	var lastWorkerIDMu sync.Mutex
	var lastWorkerID string

	mockShipyard := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/workers/register":
			atomic.AddInt32(&registerCalls, 1)
			var req types.RegisterRequest
			require.NoError(t, json.NewDecoder(r.Body).Decode(&req))
			assert.Equal(t, "test-worker", req.WorkerName)
			assert.Equal(t, "test", req.Env)
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(map[string]any{
				"code":    0,
				"message": "ok",
				"data":    types.RegisterResponse{WorkerID: "100", HeartbeatIntervalSec: 1},
			})
		default:
			// /api/workers/{id}/heartbeat
			var req types.HeartbeatRequest
			require.NoError(t, json.NewDecoder(r.Body).Decode(&req))
			atomic.AddInt32(&heartbeatCalls, 1)
			lastWorkerIDMu.Lock()
			lastWorkerID = req.WorkerID
			lastWorkerIDMu.Unlock()
			w.WriteHeader(http.StatusOK)
		}
	}))
	defer mockShipyard.Close()

	echo := NewEchoHandler(zap.NewNop(), "test-worker")
	reg := NewRegisterHandler(zap.NewNop(), RegisterConfig{
		ShipyardURL:       mockShipyard.URL,
		WorkerName:        "test-worker",
		Env:               "test",
		HeartbeatInterval: 100 * time.Millisecond,
	}, echo)

	ctx, cancel := context.WithTimeout(context.Background(), 500*time.Millisecond)
	defer cancel()

	err := reg.Start(ctx)
	require.NoError(t, err)

	// 等一会,heartbeat 应该走起来
	time.Sleep(300 * time.Millisecond)
	reg.Stop()

	// 验证
	assert.True(t, reg.IsRegistered(), "should be registered after first call")
	assert.Equal(t, "100", reg.WorkerID(), "worker ID should match mock response")
	lastWorkerIDMu.Lock()
	gotID := lastWorkerID
	lastWorkerIDMu.Unlock()
	assert.Equal(t, "100", gotID, "heartbeat should use correct worker ID")
	assert.GreaterOrEqual(t, atomic.LoadInt32(&registerCalls), int32(1), "should have called register at least once")
	assert.GreaterOrEqual(t, atomic.LoadInt32(&heartbeatCalls), int32(1), "should have called heartbeat at least once")
}

func TestEchoHandler_SetWorkerID(t *testing.T) {
	h := NewEchoHandler(zap.NewNop(), "worker")

	// 初始 workerID 是 nil
	assert.Nil(t, h.workerID)

	// 显式 set 后
	h.SetWorkerID("999")
	assert.NotNil(t, h.workerID)
	assert.Equal(t, "999", *h.workerID)
}
