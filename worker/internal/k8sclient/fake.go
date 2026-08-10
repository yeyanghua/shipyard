// Package k8sclient - fake client 本机开发用, 返 4 个真实存在的 k8s ns + mock pod/deployment.
package k8sclient

import (
	"context"
	"fmt"
	"time"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"

	"github.com/yeyanghua/shipyard/worker/internal/types"
)

// FakeClient 内存 mock, 不调真 k8s.
//
// 数据风格 "真实化" (A1): 4 个 ns 是真 k8s 默认 ns 名 + 创建时间倒推, 跟真 k3d 起来后看到的 4 个 ns 一致.
// pod/deployment 用 shipyard 自己的镜像 + 1-2 个额外 mock, 便于 UI 调试.
type FakeClient struct {
	// startTime 用于算 age (e.g. "2h")
	startTime time.Time
}

// NewFakeClient 创建一个 fake client.
func NewFakeClient() *FakeClient {
	return &FakeClient{startTime: time.Now().Add(-2 * time.Hour)}
}

// ListNamespaces 返 4 个真 k8s 默认 ns.
func (f *FakeClient) ListNamespaces(ctx context.Context) ([]types.Namespace, error) {
	now := time.Now()
	return []types.Namespace{
		{
			Name:      "default",
			Status:    "Active",
			Age:       f.formatAge(72 * time.Hour), // 集群起来通常 3 天
			Created:   now.Add(-72 * time.Hour),
		},
		{
			Name:      "kube-node-lease",
			Status:    "Active",
			Age:       f.formatAge(72 * time.Hour),
			Created:   now.Add(-72 * time.Hour),
		},
		{
			Name:      "kube-public",
			Status:    "Active",
			Age:       f.formatAge(72 * time.Hour),
			Created:   now.Add(-72 * time.Hour),
		},
		{
			Name:      "kube-system",
			Status:    "Active",
			Age:       f.formatAge(72 * time.Hour),
			Created:   now.Add(-72 * time.Hour),
		},
	}, nil
}

// ListPods 返指定 ns 的 mock pod. 其他 ns 返空.
func (f *FakeClient) ListPods(ctx context.Context, namespace string) ([]types.Pod, error) {
	if namespace == "" {
		// 跨 ns: 只返 shipyard ns 的 (跟 M8.1 mock 行为一致)
		namespace = "shipyard"
	}

	now := time.Now()

	switch namespace {
	case "shipyard":
		// shipyard 自部署 (worker 跑后) 的 pod
		return []types.Pod{
			{
				Name:      "shipyard-backend-7d4f8b6c9-x2k7m",
				Namespace: "shipyard",
				Phase:     "Running",
				Ready:     "1/1",
				Restarts:  0,
				Node:      "k3d-shipyard-server-0",
				IP:        "10.42.0.15",
				Age:       f.formatAge(2 * time.Hour),
				Created:   f.startTime,
			},
			{
				Name:      "shipyard-web-5b9c8d7f4-m8n3p",
				Namespace: "shipyard",
				Phase:     "Running",
				Ready:     "1/1",
				Restarts:  0,
				Node:      "k3d-shipyard-server-0",
				IP:        "10.42.0.16",
				Age:       f.formatAge(2 * time.Hour),
				Created:   f.startTime,
			},
			{
				Name:      "shipyard-worker-6c5d4b8f-a1b2c",
				Namespace: "shipyard",
				Phase:     "Running",
				Ready:     "1/1",
				Restarts:  0,
				Node:      "k3d-shipyard-server-0",
				IP:        "10.42.0.17",
				Age:       f.formatAge(2 * time.Hour),
				Created:   f.startTime,
			},
		}, nil
	case "kube-system":
		// k3d/k8s 系统组件
		return []types.Pod{
			{
				Name:      "coredns-7b9844c7d8-x9k2p",
				Namespace: "kube-system",
				Phase:     "Running",
				Ready:     "1/1",
				Restarts:  0,
				Node:      "k3d-shipyard-server-0",
				IP:        "10.42.0.5",
				Age:       f.formatAge(72 * time.Hour),
				Created:   now.Add(-72 * time.Hour),
			},
			{
				Name:      "local-path-provisioner-9f5d8b-x7m3n",
				Namespace: "kube-system",
				Phase:     "Running",
				Ready:     "1/1",
				Restarts:  0,
				Node:      "k3d-shipyard-server-0",
				IP:        "10.42.0.6",
				Age:       f.formatAge(72 * time.Hour),
				Created:   now.Add(-72 * time.Hour),
			},
		}, nil
	default:
		// default / kube-public / kube-node-lease 通常空
		return []types.Pod{}, nil
	}
}

// ListDeployments 返指定 ns 的 mock deployment. 其他 ns 返空.
func (f *FakeClient) ListDeployments(ctx context.Context, namespace string) ([]types.Deployment, error) {
	if namespace == "" {
		namespace = "shipyard"
	}

	switch namespace {
	case "shipyard":
		return []types.Deployment{
			{
				Name:            "shipyard-backend",
				Namespace:       "shipyard",
				Replicas:        2,
				ReadyReplicas:   2,
				Available:       2,
				UpdatedReplicas: 2,
				Image:           "ghcr.io/yeyanghua/shipyard-backend:0.1.0",
				Age:             f.formatAge(2 * time.Hour),
				Created:         f.startTime,
			},
			{
				Name:            "shipyard-web",
				Namespace:       "shipyard",
				Replicas:        1,
				ReadyReplicas:   1,
				Available:       1,
				UpdatedReplicas: 1,
				Image:           "ghcr.io/yeyanghua/shipyard-web:0.1.0",
				Age:             f.formatAge(2 * time.Hour),
				Created:         f.startTime,
			},
			{
				Name:            "shipyard-worker",
				Namespace:       "shipyard",
				Replicas:        2,
				ReadyReplicas:   2,
				Available:       2,
				UpdatedReplicas: 2,
				Image:           "shipyard-worker:dev",
				Age:             f.formatAge(2 * time.Hour),
				Created:         f.startTime,
			},
		}, nil
	case "kube-system":
		return []types.Deployment{
			{
				Name:            "coredns",
				Namespace:       "kube-system",
				Replicas:        1,
				ReadyReplicas:   1,
				Available:       1,
				UpdatedReplicas: 1,
				Image:           "registry.k8s.io/coredns/coredns:v1.11.1",
				Age:             f.formatAge(72 * time.Hour),
				Created:         time.Now().Add(-72 * time.Hour),
			},
			{
				Name:            "local-path-provisioner",
				Namespace:       "kube-system",
				Replicas:        1,
				ReadyReplicas:   1,
				Available:       1,
				UpdatedReplicas: 1,
				Image:           "docker.io/rancher/local-path-provisioner:v0.0.24",
				Age:             f.formatAge(72 * time.Hour),
				Created:         time.Now().Add(-72 * time.Hour),
			},
		}, nil
	default:
		return []types.Deployment{}, nil
	}
}

// ClusterInfo 返固定的假集群信息. 部署到真 k8s 时由 inClusterClient 覆盖.
func (f *FakeClient) ClusterInfo(ctx context.Context) (version string, nodeName string, err error) {
	// 用 metav1 类型的导入, 避免 go 报 unused import (k8s.io/apimachinery 在真客户端会用到)
	_ = metav1.Now()
	return "v1.30.3-fake", "k3d-shipyard-server-0", nil
}

// formatAge 把 duration 渲染成人类可读格式. e.g. "2h", "30m", "5d".
func (f *FakeClient) formatAge(d time.Duration) string {
	if d < time.Minute {
		return fmt.Sprintf("%ds", int(d.Seconds()))
	}
	if d < time.Hour {
		return fmt.Sprintf("%dm", int(d.Minutes()))
	}
	if d < 24*time.Hour {
		return fmt.Sprintf("%dh", int(d.Hours()))
	}
	return fmt.Sprintf("%dd", int(d.Hours()/24))
}
