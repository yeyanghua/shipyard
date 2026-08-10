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
}
