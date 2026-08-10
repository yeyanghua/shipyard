package handler

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"
)

func newHealthRouter(h *HealthHandler) *gin.Engine {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.GET("/healthz", h.Liveness)
	r.GET("/readyz", h.Readiness)
	return r
}

func TestHealthHandler_Liveness(t *testing.T) {
	h := NewHealthHandler(zap.NewNop(), "1.0.0-test")
	r := newHealthRouter(h)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodGet, "/healthz", nil)
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]any
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "ok", resp["status"])
	assert.NotEmpty(t, resp["uptime"], "uptime should be present")
}

func TestHealthHandler_Readiness(t *testing.T) {
	h := NewHealthHandler(zap.NewNop(), "1.0.0-test")
	r := newHealthRouter(h)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodGet, "/readyz", nil)
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]any
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "ok", resp["status"])
	assert.Equal(t, "1.0.0-test", resp["version"])
}
