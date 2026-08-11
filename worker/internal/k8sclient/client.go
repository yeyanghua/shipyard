// Package k8sclient 封装 k8s.io/client-go, 让 worker 调 k8s API.
//
// 两种实现:
//   - inClusterClient: 真 k8s (ServiceAccount token 鉴权), 部署到 k8s 集群时用
//   - fakeClient: 内存 mock (4 个真实 ns 名 + 假 pod/deployment), 本机开发用
//
// 切换靠 K8S_IN_CLUSTER env var (cmd/worker/main.go 选).
package k8sclient

import (
	"context"

	"github.com/yeyanghua/shipyard/worker/internal/types"
)

// K8sClient worker 调 k8s API 的抽象.
//
// 命名跟 cluster handler 返的 DTO 对齐 (Namespace/Pod/Deployment), 实现细节不外泄.
//
// M9 commit-7 加 4 deploy 方法 (Apply / Rollback / Scale / GetManifest), 走
// client-go DynamicClient + unstructured (不写死 schema, 任意 K8s 资源可 apply).
type K8sClient interface {
	// ListNamespaces 列所有 namespace.
	ListNamespaces(ctx context.Context) ([]types.Namespace, error)

	// ListPods 列出指定 ns 的 pod. namespace 为空返所有 ns 的 pod.
	ListPods(ctx context.Context, namespace string) ([]types.Pod, error)

	// ListDeployments 列出指定 ns 的 deployment. namespace 为空返所有 ns 的 deployment.
	ListDeployments(ctx context.Context, namespace string) ([]types.Deployment, error)

	// ClusterInfo 拿集群基本信息 (注册 shipyard 时上报).
	// - version: k8s server version (e.g. "v1.30.3")
	// - nodeName: 当前 worker 跑的节点名 (e.g. "k3d-shipyard-server-0")
	ClusterInfo(ctx context.Context) (version string, nodeName string, err error)

	// ============================================================
	// M9 commit-7: deploy 写动作 — 走 client-go DynamicClient + unstructured
	// ============================================================

	// Apply 把 shipyard 渲染完的 yaml apply 到 k8s.
	//
	// 流程: 解析 multi-doc yaml (--- 分隔) → 拿 gvr (group/version/resource) →
	// 拿名字 + namespace → 查存在不 → 存在 Update, 不存在 Create.
	// 不写死 schema (Deployment / Service / ConfigMap 都支持).
	//
	// @param yaml shipyard 渲染的完整 yaml (可能含 --- 分隔的多个 resource)
	// @return phase ("created" / "updated" / "unchanged"), message, manifest yaml
	Apply(ctx context.Context, namespace, yaml string) (phase string, message string, manifest string, err error)

	// Rollback — shipyard 端拿历史 snapshot 的 yaml 重发, worker 走 Apply 就行.
	// 接口跟 Apply 完全一样, 这里保留单独方法是为了未来 worker 端能做更智能的回滚
	// (比如 kubectl rollout undo 拿 revision 历史).
	Rollback(ctx context.Context, namespace, yaml string) (phase string, message string, manifest string, err error)

	// Scale 修改资源副本数.
	// @param kind 资源 kind (例 "Deployment" / "StatefulSet")
	// @param name 资源名
	// @param replicas 目标副本数
	Scale(ctx context.Context, namespace, kind, name string, replicas int) (phase string, message string, err error)

	// GetManifest 拿 k8s 真生效的 spec (高级模式 diff 用).
	// @return manifest yaml 字符串 (k8s 真生效, 跟 shipyard 提交的 yaml 可能不同 — k8s 会补默认字段)
	GetManifest(ctx context.Context, namespace, kind, name string) (manifest string, err error)
}
