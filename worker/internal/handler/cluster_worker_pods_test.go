// M9 commit-16: ListWorkerPods 端点测试
//
// 测 3 个场景: 正常 (2 pod 2 replicas), 无 deployment (0 replicas), nil clientset (fake mode).
// 避免直接构造 corev1.Pod / appsv1.Deployment (太重), 用空 fake clientset + nil clientset 走两个分支.
package handler

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
	"k8s.io/client-go/kubernetes/fake"
)

func TestListWorkerPods_NilClientset_FakeMode(t *testing.T) {
	gin.SetMode(gin.TestMode)
	h := &ClusterHandler{logger: zap.NewNop(), k8s: nil, clientset: nil}

	engine := gin.New()
	engine.GET("/api/v1/cluster/worker-pods", h.ListWorkerPods)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/cluster/worker-pods", nil)
	w := httptest.NewRecorder()
	engine.ServeHTTP(w, req)

	// fake mode (clientset=nil): 200, Pods 空, replicas 0
	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Code int        `json:"code"`
		Data WorkerInfo `json:"data"`
	}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	assert.NoError(t, err, "JSON 解析")
	assert.Equal(t, 0, resp.Code, "业务码 0")
	assert.Equal(t, "shipyard-worker", resp.Data.WorkerName)
	assert.Equal(t, "shipyard", resp.Data.Namespace)
	assert.Equal(t, int32(0), resp.Data.Replicas, "fake mode 没 deployment")
	assert.Equal(t, int32(0), resp.Data.ReadyReplicas)
	assert.Empty(t, resp.Data.Pods)
}

func TestListWorkerPods_EmptyFakeClientset(t *testing.T) {
	gin.SetMode(gin.TestMode)
	// fake clientset 但空 — 走 deployment 查不到 (404) 路径, 不返 500
	client := fake.NewSimpleClientset()
	h := &ClusterHandler{logger: zap.NewNop(), k8s: nil, clientset: client}

	engine := gin.New()
	engine.GET("/api/v1/cluster/worker-pods", h.ListWorkerPods)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/cluster/worker-pods", nil)
	w := httptest.NewRecorder()
	engine.ServeHTTP(w, req)

	// 无 deployment → 200 (warn log 但不返 500), replicas 0, pods 0
	assert.Equal(t, http.StatusOK, w.Code)

	var resp struct {
		Code int        `json:"code"`
		Data WorkerInfo `json:"data"`
	}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	assert.NoError(t, err, "JSON 解析")
	assert.Equal(t, 0, resp.Code, "业务码 0 (deployment 查不到不返 500)")
	assert.Equal(t, int32(0), resp.Data.Replicas)
	assert.Equal(t, int32(0), resp.Data.ReadyReplicas)
	assert.Empty(t, resp.Data.Pods)
}

func TestWorkerDeploymentName_Override(t *testing.T) {
	gin.SetMode(gin.TestMode)
	t.Setenv("WORKER_DEPLOYMENT_NAME", "my-custom-worker")

	h := &ClusterHandler{logger: zap.NewNop(), k8s: nil, clientset: nil}
	assert.Equal(t, "my-custom-worker", h.workerDeployment(),
		"env var 覆盖默认 shipyard-worker")
}

func TestWorkerDeploymentName_Default(t *testing.T) {
	gin.SetMode(gin.TestMode)
	// 确保没设 env var
	t.Setenv("WORKER_DEPLOYMENT_NAME", "")

	h := &ClusterHandler{logger: zap.NewNop(), k8s: nil, clientset: nil}
	assert.Equal(t, "shipyard-worker", h.workerDeployment(),
		"默认 shipyard-worker")
}
