// Package handler - deploy_test.go M9 commit-8: deploy handler 3 端点测试.
package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"

	"github.com/yeyanghua/shipyard/worker/internal/types"
)

// mockDeployK8sClient 模拟 K8sClient 4 deploy 方法, 返固定 phase/message/manifest.
type mockDeployK8sClient struct {
	applyPhase    string
	applyMessage  string
	applyManifest string
	applyErr      error

	scalePhase   string
	scaleMessage string
	scaleErr     error

	manifest string
	getErr   error
}

func (m *mockDeployK8sClient) ListNamespaces(ctx context.Context) ([]types.Namespace, error) {
	return nil, nil
}
func (m *mockDeployK8sClient) ListPods(ctx context.Context, ns string) ([]types.Pod, error) {
	return nil, nil
}
func (m *mockDeployK8sClient) ListDeployments(ctx context.Context, ns string) ([]types.Deployment, error) {
	return nil, nil
}
func (m *mockDeployK8sClient) ClusterInfo(ctx context.Context) (string, string, error) {
	return "v1.30.3", "test-node", nil
}

func (m *mockDeployK8sClient) Apply(ctx context.Context, ns, yamlStr string) (string, string, string, error) {
	return m.applyPhase, m.applyMessage, m.applyManifest, m.applyErr
}
func (m *mockDeployK8sClient) Rollback(ctx context.Context, ns, yamlStr string) (string, string, string, error) {
	return m.applyPhase, m.applyMessage, m.applyManifest, m.applyErr
}
func (m *mockDeployK8sClient) Scale(ctx context.Context, ns, kind, name string, replicas int) (string, string, error) {
	return m.scalePhase, m.scaleMessage, m.scaleErr
}
func (m *mockDeployK8sClient) GetManifest(ctx context.Context, ns, kind, name string) (string, error) {
	return m.manifest, m.getErr
}

func newDeployTestRouter(d *DeployHandler) *gin.Engine {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	tg := r.Group("/api/v1/tasks")
	{
		tg.POST("/deploy", d.Deploy)
		tg.POST("/scale", d.Scale)
		tg.GET("/manifest", d.GetManifest)
	}
	return r
}

// TestDeployHandler_Deploy_Success: 正常 apply 返 200 + phase=created.
func TestDeployHandler_Deploy_Success(t *testing.T) {
	mock := &mockDeployK8sClient{
		applyPhase:    "created",
		applyMessage:  "deployment.apps/myapp created",
		applyManifest: "apiVersion: apps/v1\nkind: Deployment\n...",
	}
	d := NewDeployHandler(zap.NewNop(), mock)
	r := newDeployTestRouter(d)

	body, _ := json.Marshal(map[string]any{
		"namespace":     "shipyard-dev",
		"yaml":          "apiVersion: apps/v1\nkind: Deployment",
		"resourceName":  "myapp-dev",
		"deployRecordId": 1234,
	})
	req, _ := http.NewRequest("POST", "/api/v1/tasks/deploy", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("期望 200, 实际 %d, body: %s", w.Code, w.Body.String())
	}

	var resp struct {
		Code    int                    `json:"code"`
		Message string                 `json:"message"`
		Data    types.DeployTaskResponse `json:"data"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("json 解析失败: %v", err)
	}
	if resp.Code != 0 {
		t.Errorf("code 应该 0, 实际 %d", resp.Code)
	}
	if resp.Data.Phase != "created" {
		t.Errorf("phase 应该 'created', 实际 '%s'", resp.Data.Phase)
	}
	if !strings.Contains(resp.Data.Message, "myapp created") {
		t.Errorf("message 应含 'myapp created', 实际 '%s'", resp.Data.Message)
	}
	if resp.Data.Manifest == "" {
		t.Errorf("manifest 不能为空")
	}
}

// TestDeployHandler_Deploy_BadRequest: 缺 yaml → 400.
func TestDeployHandler_Deploy_BadRequest(t *testing.T) {
	mock := &mockDeployK8sClient{}
	d := NewDeployHandler(zap.NewNop(), mock)
	r := newDeployTestRouter(d)

	// 缺 yaml
	body, _ := json.Marshal(map[string]any{
		"namespace": "shipyard-dev",
	})
	req, _ := http.NewRequest("POST", "/api/v1/tasks/deploy", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("期望 400, 实际 %d", w.Code)
	}
}

// TestDeployHandler_Deploy_K8sError: k8s apply 失败 → 500 + phase=failed.
func TestDeployHandler_Deploy_K8sError(t *testing.T) {
	mock := &mockDeployK8sClient{
		applyPhase:   "failed",
		applyMessage: "image pull error",
		applyErr:     &simpleErr{msg: "k8s api error"},
	}
	d := NewDeployHandler(zap.NewNop(), mock)
	r := newDeployTestRouter(d)

	body, _ := json.Marshal(map[string]any{
		"namespace": "shipyard-dev",
		"yaml":      "apiVersion: apps/v1\nkind: Deployment",
	})
	req, _ := http.NewRequest("POST", "/api/v1/tasks/deploy", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusInternalServerError {
		t.Errorf("期望 500, 实际 %d", w.Code)
	}
	if !strings.Contains(w.Body.String(), "image pull error") {
		t.Errorf("body 应含 'image pull error', 实际: %s", w.Body.String())
	}
}

// TestDeployHandler_Scale_Success: 改副本数 1→3, 返 phase=scaled.
func TestDeployHandler_Scale_Success(t *testing.T) {
	mock := &mockDeployK8sClient{
		scalePhase:   "scaled",
		scaleMessage: "Deployment/myapp scaled to 3",
	}
	d := NewDeployHandler(zap.NewNop(), mock)
	r := newDeployTestRouter(d)

	body, _ := json.Marshal(map[string]any{
		"namespace": "shipyard-dev",
		"kind":      "Deployment",
		"name":      "myapp",
		"replicas":  3,
	})
	req, _ := http.NewRequest("POST", "/api/v1/tasks/scale", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("期望 200, 实际 %d", w.Code)
	}
	var resp struct {
		Code int                    `json:"code"`
		Data types.ScaleTaskResponse `json:"data"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("json 解析失败: %v", err)
	}
	if resp.Data.Phase != "scaled" {
		t.Errorf("phase 应为 'scaled', 实际 '%s'", resp.Data.Phase)
	}
}

// TestDeployHandler_GetManifest_Success: 返 raw yaml 字符串 (Content-Type=application/yaml).
func TestDeployHandler_GetManifest_Success(t *testing.T) {
	mock := &mockDeployK8sClient{
		manifest: "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: myapp",
	}
	d := NewDeployHandler(zap.NewNop(), mock)
	r := newDeployTestRouter(d)

	req, _ := http.NewRequest("GET", "/api/v1/tasks/manifest?kind=Deployment&name=myapp&namespace=shipyard-dev", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("期望 200, 实际 %d", w.Code)
	}
	if !strings.Contains(w.Body.String(), "kind: Deployment") {
		t.Errorf("body 应含 'kind: Deployment', 实际: %s", w.Body.String())
	}
}

// TestDeployHandler_GetManifest_MissingQuery: 缺 query 参数 → 400.
func TestDeployHandler_GetManifest_MissingQuery(t *testing.T) {
	mock := &mockDeployK8sClient{}
	d := NewDeployHandler(zap.NewNop(), mock)
	r := newDeployTestRouter(d)

	req, _ := http.NewRequest("GET", "/api/v1/tasks/manifest?kind=Deployment", nil) // 缺 name + namespace
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("期望 400, 实际 %d", w.Code)
	}
}
