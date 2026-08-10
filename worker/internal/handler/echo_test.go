package handler

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	"github.com/yeyanghua/shipyard/worker/internal/types"
)

func newEchoRouter(h *EchoHandler) *gin.Engine {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.POST("/api/v1/tasks/echo", h.Echo)
	return r
}

func TestEchoHandler_Echo_HappyPath(t *testing.T) {
	h := NewEchoHandler(zap.NewNop(), "worker-test-01")
	r := newEchoRouter(h)

	reqBody := types.EchoRequest{
		Message:   "hello from shipyard",
		Timestamp: 1234567890,
		Extra:     map[string]interface{}{"trace_id": "abc-123"},
	}
	body, _ := json.Marshal(reqBody)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodPost, "/api/v1/tasks/echo", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Code    int                `json:"code"`
		Message string             `json:"message"`
		Data    types.EchoResponse `json:"data"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 0, resp.Code)
	assert.Equal(t, "worker-test-01", resp.Data.WorkerName)
	assert.Equal(t, "hello from shipyard", resp.Data.Received.Message)
	assert.Equal(t, int64(1234567890), resp.Data.Received.Timestamp)
	assert.Equal(t, "abc-123", resp.Data.Received.Extra["trace_id"])
	assert.NotZero(t, resp.Data.ProcessedAt)
}

func TestEchoHandler_Echo_WithWorkerID(t *testing.T) {
	h := NewEchoHandler(zap.NewNop(), "worker-test-02")
	h.SetWorkerID(42)
	r := newEchoRouter(h)

	reqBody := types.EchoRequest{Message: "test"}
	body, _ := json.Marshal(reqBody)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodPost, "/api/v1/tasks/echo", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Data types.EchoResponse `json:"data"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, int64(42), resp.Data.WorkerID)
}

func TestEchoHandler_Echo_BadJSON(t *testing.T) {
	h := NewEchoHandler(zap.NewNop(), "worker-test-03")
	r := newEchoRouter(h)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodPost, "/api/v1/tasks/echo", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestEchoHandler_Echo_EmptyBody(t *testing.T) {
	h := NewEchoHandler(zap.NewNop(), "worker-test-04")
	r := newEchoRouter(h)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodPost, "/api/v1/tasks/echo", bytes.NewReader([]byte("{}")))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)

	// 空 body 也合法,message 字段为空
	assert.Equal(t, http.StatusOK, w.Code)
}
