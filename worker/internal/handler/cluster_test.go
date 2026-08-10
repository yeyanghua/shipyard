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

	"github.com/yeyanghua/shipyard/worker/internal/types"
)

func newTestRouter(h *ClusterHandler) *gin.Engine {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	v1 := r.Group("/api/v1")
	v1.GET("/cluster/namespaces", h.ListNamespaces)
	v1.GET("/cluster/pods", h.ListPods)
	v1.GET("/cluster/deployments", h.ListDeployments)
	return r
}

func TestClusterHandler_ListNamespaces(t *testing.T) {
	h := NewClusterHandler(zap.NewNop())
	r := newTestRouter(h)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodGet, "/api/v1/cluster/namespaces", nil)
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Code    int               `json:"code"`
		Message string            `json:"message"`
		Data    []types.Namespace `json:"data"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 0, resp.Code)
	assert.NotEmpty(t, resp.Data, "should return at least one mock namespace")

	// 验证一定有 default / kube-system / shipyard
	names := make(map[string]bool)
	for _, ns := range resp.Data {
		names[ns.Name] = true
	}
	assert.True(t, names["default"], "should include default namespace")
	assert.True(t, names["kube-system"], "should include kube-system namespace")
}

func TestClusterHandler_ListPods_DefaultNamespace(t *testing.T) {
	h := NewClusterHandler(zap.NewNop())
	r := newTestRouter(h)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodGet, "/api/v1/cluster/pods?namespace=shipyard", nil)
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Code int         `json:"code"`
		Data []types.Pod `json:"data"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 0, resp.Code)
	assert.NotEmpty(t, resp.Data, "should return at least one mock pod")
	for _, p := range resp.Data {
		assert.Equal(t, "shipyard", p.Namespace)
		assert.Equal(t, "Running", p.Phase)
	}
}

func TestClusterHandler_ListPods_DefaultQueryParam(t *testing.T) {
	// 不传 namespace 走默认 default
	h := NewClusterHandler(zap.NewNop())
	r := newTestRouter(h)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodGet, "/api/v1/cluster/pods", nil)
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
}

func TestClusterHandler_ListDeployments(t *testing.T) {
	h := NewClusterHandler(zap.NewNop())
	r := newTestRouter(h)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodGet, "/api/v1/cluster/deployments?namespace=shipyard", nil)
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Code int                `json:"code"`
		Data []types.Deployment `json:"data"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 0, resp.Code)
	assert.NotEmpty(t, resp.Data)
	for _, d := range resp.Data {
		assert.Equal(t, "shipyard", d.Namespace)
		assert.Greater(t, d.Replicas, int32(0))
		assert.NotEmpty(t, d.Image)
	}
}
