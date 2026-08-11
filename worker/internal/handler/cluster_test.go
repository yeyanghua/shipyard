package handler

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"

	"github.com/yeyanghua/shipyard/worker/internal/k8sclient"
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

// 用 fake client 做测试 (M8.3 起, 替硬编码 mock)
func newHandlerWithFake() *ClusterHandler {
	return NewClusterHandler(zap.NewNop(), k8sclient.NewFakeClient(), nil)
}

func TestClusterHandler_ListNamespaces(t *testing.T) {
	h := newHandlerWithFake()
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
	assert.NotEmpty(t, resp.Data, "fake client should return namespaces")

	// fake 返 4 个真 k8s 默认 ns
	names := make(map[string]bool)
	for _, ns := range resp.Data {
		names[ns.Name] = true
	}
	assert.True(t, names["default"], "should include default namespace")
	assert.True(t, names["kube-system"], "should include kube-system namespace")
	assert.True(t, names["kube-public"], "should include kube-public namespace")
	assert.True(t, names["kube-node-lease"], "should include kube-node-lease namespace")
}

func TestClusterHandler_ListPods_DefaultNamespace(t *testing.T) {
	h := newHandlerWithFake()
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
	assert.NotEmpty(t, resp.Data, "should return at least one fake pod")
	for _, p := range resp.Data {
		assert.Equal(t, "shipyard", p.Namespace)
		assert.Equal(t, "Running", p.Phase)
	}
}

func TestClusterHandler_ListPods_AllNamespaces(t *testing.T) {
	// 不传 namespace → 跨 ns, fake client 默认返 shipyard
	h := newHandlerWithFake()
	r := newTestRouter(h)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodGet, "/api/v1/cluster/pods", nil)
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
}

func TestClusterHandler_ListPods_KubeSystem(t *testing.T) {
	h := newHandlerWithFake()
	r := newTestRouter(h)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodGet, "/api/v1/cluster/pods?namespace=kube-system", nil)
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Code int         `json:"code"`
		Data []types.Pod `json:"data"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 0, resp.Code)
	// kube-system 应该返 coredns + local-path-provisioner
	assert.Equal(t, 2, len(resp.Data))
}

func TestClusterHandler_ListPods_EmptyNamespace(t *testing.T) {
	// default / kube-public / kube-node-lease fake client 返空
	h := newHandlerWithFake()
	r := newTestRouter(h)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodGet, "/api/v1/cluster/pods?namespace=default", nil)
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Code int         `json:"code"`
		Data []types.Pod `json:"data"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 0, resp.Code)
	assert.Empty(t, resp.Data)
}

func TestClusterHandler_ListDeployments(t *testing.T) {
	h := newHandlerWithFake()
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

// mockFailingClient 用于测 k8s API 失败时 handler 返 500 兜底.
type mockFailingClient struct{}

func (m *mockFailingClient) ListNamespaces(ctx context.Context) ([]types.Namespace, error) {
	return nil, errFake("fake k8s unreachable")
}
func (m *mockFailingClient) ListPods(ctx context.Context, ns string) ([]types.Pod, error) {
	return nil, errFake("fake k8s unreachable")
}
func (m *mockFailingClient) ListDeployments(ctx context.Context, ns string) ([]types.Deployment, error) {
	return nil, errFake("fake k8s unreachable")
}
func (m *mockFailingClient) ClusterInfo(ctx context.Context) (string, string, error) {
	return "?", "?", errFake("fake k8s unreachable")
}

// M9 commit-7: 4 deploy 方法都返 fake err
func (m *mockFailingClient) Apply(ctx context.Context, namespace, yamlStr string) (string, string, string, error) {
	return "", "", "", errFake("fake k8s unreachable")
}
func (m *mockFailingClient) Rollback(ctx context.Context, namespace, yamlStr string) (string, string, string, error) {
	return "", "", "", errFake("fake k8s unreachable")
}
func (m *mockFailingClient) Scale(ctx context.Context, namespace, kind, name string, replicas int) (string, string, error) {
	return "", "", errFake("fake k8s unreachable")
}
func (m *mockFailingClient) GetManifest(ctx context.Context, namespace, kind, name string) (string, error) {
	return "", errFake("fake k8s unreachable")
}

type simpleErr struct{ msg string }

func (e *simpleErr) Error() string { return e.msg }

func errFake(msg string) error { return &simpleErr{msg: msg} }

func TestClusterHandler_K8sAPIFails_Returns500(t *testing.T) {
	h := NewClusterHandler(zap.NewNop(), &mockFailingClient{}, nil)
	r := newTestRouter(h)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodGet, "/api/v1/cluster/namespaces", nil)
	r.ServeHTTP(w, req)

	assert.Equal(t, http.StatusInternalServerError, w.Code)

	var resp struct {
		Code    int    `json:"code"`
		Message string `json:"message"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 500, resp.Code)
	assert.Contains(t, resp.Message, "fake k8s unreachable")
}
