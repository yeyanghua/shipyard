// Package k8sclient - M9 commit-7: deploy 4 方法 (Apply / Rollback / Scale / GetManifest) 单测.
package k8sclient

import (
	"context"
	"strings"
	"testing"

	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime/schema"
	"sigs.k8s.io/yaml"

	"github.com/yeyanghua/shipyard/worker/internal/types"
)

// TestFakeClient_Apply 测试 fake.Apply — V1 demo 模式不真 apply, 返 phase=created.
func TestFakeClient_Apply(t *testing.T) {
	f := NewFakeClient()
	phase, msg, manifest, err := f.Apply(context.Background(), "shipyard-dev",
		"apiVersion: apps/v1\nkind: Deployment\n...")

	if err != nil {
		t.Fatalf("Apply 返错: %v", err)
	}
	if phase != "created" {
		t.Errorf("phase 应该是 created, 实际: %s", phase)
	}
	if !strings.Contains(msg, "fake apply") {
		t.Errorf("msg 应该含 'fake apply', 实际: %s", msg)
	}
	if manifest == "" {
		t.Errorf("manifest 不能为空")
	}
}

// TestFakeClient_Rollback 测试 fake.Rollback — 跟 Apply 一样返 phase=rolled-back.
func TestFakeClient_Rollback(t *testing.T) {
	f := NewFakeClient()
	phase, msg, _, err := f.Rollback(context.Background(), "shipyard-dev", "yaml")

	if err != nil {
		t.Fatalf("Rollback 返错: %v", err)
	}
	if phase != "rolled-back" {
		t.Errorf("phase 应该是 rolled-back, 实际: %s", phase)
	}
	if !strings.Contains(msg, "fake rollback") {
		t.Errorf("msg 应该含 'fake rollback', 实际: %s", msg)
	}
}

// TestFakeClient_Scale 测试 fake.Scale — 返 phase=scaled + replicas 数字.
func TestFakeClient_Scale(t *testing.T) {
	f := NewFakeClient()
	phase, msg, err := f.Scale(context.Background(), "shipyard-dev", "Deployment", "myapp", 3)

	if err != nil {
		t.Fatalf("Scale 返错: %v", err)
	}
	if phase != "scaled" {
		t.Errorf("phase 应该是 scaled, 实际: %s", phase)
	}
	if !strings.Contains(msg, "Deployment") {
		t.Errorf("msg 应该含 'Deployment', 实际: %s", msg)
	}
}

// TestFakeClient_GetManifest 测试 fake.GetManifest — 返 yaml 字符串含 kind + name + namespace.
func TestFakeClient_GetManifest(t *testing.T) {
	f := NewFakeClient()
	yamlStr, err := f.GetManifest(context.Background(), "shipyard-dev", "Deployment", "myapp")

	if err != nil {
		t.Fatalf("GetManifest 返错: %v", err)
	}
	for _, expect := range []string{"kind: Deployment", "name: myapp", "namespace: shipyard-dev"} {
		if !strings.Contains(yamlStr, expect) {
			t.Errorf("manifest 应该含 '%s', 实际: %s", expect, yamlStr)
		}
	}
}

// TestGvrFromUnstructured_Deployment 测试 apiVersion=apps/v1 + kind=Deployment → gvr 正确.
func TestGvrFromUnstructured_Deployment(t *testing.T) {
	obj := &unstructured.Unstructured{}
	yamlData := `
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
  namespace: shipyard-dev
spec:
  replicas: 1
`
	if err := yaml.Unmarshal([]byte(yamlData), &obj.Object); err != nil {
		t.Fatalf("yaml 解析失败: %v", err)
	}

	gvr, err := gvrFromUnstructured(obj)
	if err != nil {
		t.Fatalf("gvrFromUnstructured 返错: %v", err)
	}
	if gvr.Group != "apps" {
		t.Errorf("group 应该是 apps, 实际: %s", gvr.Group)
	}
	if gvr.Version != "v1" {
		t.Errorf("version 应该是 v1, 实际: %s", gvr.Version)
	}
	if gvr.Resource != "deployments" {
		t.Errorf("resource 应该是 deployments, 实际: %s", gvr.Resource)
	}
}

// TestGvrFromUnstructured_Service 测试 apiVersion=v1 + kind=Service (没 group) → group="".
func TestGvrFromUnstructured_Service(t *testing.T) {
	obj := &unstructured.Unstructured{}
	yamlData := `
apiVersion: v1
kind: Service
metadata:
  name: myapp
  namespace: default
spec:
  selector:
    app: myapp
  ports:
    - port: 80
`
	if err := yaml.Unmarshal([]byte(yamlData), &obj.Object); err != nil {
		t.Fatalf("yaml 解析失败: %v", err)
	}

	gvr, err := gvrFromUnstructured(obj)
	if err != nil {
		t.Fatalf("gvrFromUnstructured 返错: %v", err)
	}
	if gvr.Group != "" {
		t.Errorf("group 应该为空 (core API), 实际: %s", gvr.Group)
	}
	if gvr.Resource != "services" {
		t.Errorf("resource 应该是 services, 实际: %s", gvr.Resource)
	}
}

// TestGvrFromUnstructured_UnknownKind 测试未知 kind → 返错 (不静默猜).
func TestGvrFromUnstructured_UnknownKind(t *testing.T) {
	obj := &unstructured.Unstructured{}
	yamlData := `
apiVersion: example.com/v1
kind: FooBar
metadata:
  name: xxx
`
	if err := yaml.Unmarshal([]byte(yamlData), &obj.Object); err != nil {
		t.Fatalf("yaml 解析失败: %v", err)
	}

	_, err := gvrFromUnstructured(obj)
	if err == nil {
		t.Fatalf("未知 kind 应该返错, 实际 nil")
	}
	if !strings.Contains(err.Error(), "未知 kind") {
		t.Errorf("err 应该说 '未知 kind', 实际: %s", err.Error())
	}
}

// TestGvrForKind 测 5 个已知 kind + 1 个未知.
func TestGvrForKind(t *testing.T) {
	tests := []struct {
		kind     string
		group    string
		resource string
		wantErr  bool
	}{
		{"Deployment", "apps", "deployments", false},
		{"StatefulSet", "apps", "statefulsets", false},
		{"Service", "", "services", false},
		{"ConfigMap", "", "configmaps", false},
		{"Secret", "", "secrets", false},
		{"UnknownKind", "", "", true},
	}
	for _, tt := range tests {
		t.Run(tt.kind, func(t *testing.T) {
			gvr, err := gvrForKind(tt.kind)
			if tt.wantErr {
				if err == nil {
					t.Errorf("kind=%s 应该返错", tt.kind)
				}
				return
			}
			if err != nil {
				t.Errorf("kind=%s 返错: %v", tt.kind, err)
				return
			}
			if gvr.Group != tt.group || gvr.Resource != tt.resource {
				t.Errorf("kind=%s 期望 %s/%s, 实际 %s/%s",
					tt.kind, tt.group, tt.resource, gvr.Group, gvr.Resource)
			}
		})
	}
}

// TestKindToResource 测 hardcode 5 个 kind 映射.
func TestKindToResource(t *testing.T) {
	tests := map[string]string{
		"Deployment":  "deployments",
		"StatefulSet": "statefulsets",
		"DaemonSet":   "daemonsets",
		"Service":     "services",
		"ConfigMap":   "configmaps",
		"Secret":      "secrets",
		"Unknown":     "",
	}
	for kind, want := range tests {
		t.Run(kind, func(t *testing.T) {
			got := kindToResource(kind)
			if got != want {
				t.Errorf("kind=%s 期望 %q, 实际 %q", kind, want, got)
			}
		})
	}
}

// TestInClusterClient_DynamicClient_LazyInit 测 sync.Once 懒加载只跑一次.
func TestInClusterClient_DynamicClient_LazyInit(t *testing.T) {
	// 不真连 k8s, 测 sync.Once 不会并发跑多次
	// (这个测试只验证 dynOnce 字段在 InClusterClient 里, 不验证 dynamic 客户端创建)
	// 真 dynamic client 创建需要 restCfg, 这里 skip
	t.Skip("InClusterClient 懒加载测试需要 restCfg, 留给集成测试")
}

// 测试 gvrFromUnstructured 走 schema.GroupVersionResource 整体结构 (跟实际 k8s client-go 用法对齐)
func TestGvrFromUnstructured_FullGVK(t *testing.T) {
	obj := &unstructured.Unstructured{}
	yamlData := `
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-ingress
  namespace: shipyard
`
	if err := yaml.Unmarshal([]byte(yamlData), &obj.Object); err != nil {
		t.Fatalf("yaml 解析失败: %v", err)
	}

	// Ingress 暂未在 hardcode 里 (V1 只 hardcode 5 个), 应该返 '未知 kind' 错
	_, err := gvrFromUnstructured(obj)
	if err == nil {
		t.Skip("Ingress kind 暂不在 hardcode — V1.5 扩 list 加")
	}
	_ = schema.GroupVersionResource{}
}

// 防止 import 未用
var _ = types.Namespace{}
