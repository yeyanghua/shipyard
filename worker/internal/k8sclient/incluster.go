// Package k8sclient - in-cluster client 用 client-go 调真 k8s API (ServiceAccount 鉴权).
package k8sclient

import (
	"context"
	"fmt"
	"os"
	"path/filepath"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/clientcmd"

	"github.com/yeyanghua/shipyard/worker/internal/types"
)

// InClusterClient 调真 k8s (worker 跑在 k8s pod 里).
//
// 鉴权: 自动读 /var/run/secrets/kubernetes.io/serviceaccount/ 下的 token/ca.crt
//       跟 k8s API server 走 HTTPS 通信.
//
// 部署到 k3d 后, worker 容器里:
//
//	ls /var/run/secrets/kubernetes.io/serviceaccount/
//	# ca.crt  namespace  token
type InClusterClient struct {
	clientset *kubernetes.Clientset
	nodeName  string // 从 pod spec 读, 走 downward API
}

// NewInClusterClient 创建真 k8s 客户端.
//
// 优先 in-cluster 模式 (从 /var/run/secrets/kubernetes.io/serviceaccount/ 读配置),
// 失败 fallback 到 KUBECONFIG 环境变量 (Mac 本地用 $HOME/.kube/config, 方便调试).
//
// 参数 kubeConfigPath: K8S_IN_CLUSTER=false 时读这个 (空字符串用 KUBECONFIG env, 再空用 $HOME/.kube/config)
func NewInClusterClient(kubeConfigPath string) (*InClusterClient, error) {
	var config *rest.Config
	var err error

	// 优先 in-cluster
	if isInCluster() {
		config, err = rest.InClusterConfig()
		if err != nil {
			return nil, fmt.Errorf("in-cluster config 失败: %w", err)
		}
	} else {
		// fallback: kubeconfig
		kubeConfigPath = resolveKubeConfigPath(kubeConfigPath)
		config, err = clientcmd.BuildConfigFromFlags("", kubeConfigPath)
		if err != nil {
			return nil, fmt.Errorf("kubeconfig 加载失败 (%s): %w", kubeConfigPath, err)
		}
	}

	clientset, err := kubernetes.NewForConfig(config)
	if err != nil {
		return nil, fmt.Errorf("clientset 创建失败: %w", err)
	}

	// nodeName 优先从 env (pod downward API 注入), 否则 "unknown"
	nodeName := os.Getenv("NODE_NAME")
	if nodeName == "" {
		nodeName = "unknown"
	}

	return &InClusterClient{
		clientset: clientset,
		nodeName:  nodeName,
	}, nil
}

// isInCluster 检测是否在 k8s pod 里. 看 ServiceAccount token 文件存在不.
func isInCluster() bool {
	_, err := os.Stat("/var/run/secrets/kubernetes.io/serviceaccount/token")
	return err == nil
}

// resolveKubeConfigPath 优先级: 显式参数 > KUBECONFIG env > $HOME/.kube/config
func resolveKubeConfigPath(explicit string) string {
	if explicit != "" {
		return explicit
	}
	if env := os.Getenv("KUBECONFIG"); env != "" {
		return env
	}
	home, err := os.UserHomeDir()
	if err == nil {
		return filepath.Join(home, ".kube", "config")
	}
	return ""
}

// ListNamespaces 调 CoreV1().Namespaces().List() 转 DTO.
func (c *InClusterClient) ListNamespaces(ctx context.Context) ([]types.Namespace, error) {
	list, err := c.clientset.CoreV1().Namespaces().List(ctx, metav1.ListOptions{})
	if err != nil {
		return nil, fmt.Errorf("列出 namespace 失败: %w", err)
	}

	result := make([]types.Namespace, 0, len(list.Items))
	for _, ns := range list.Items {
		result = append(result, types.Namespace{
			Name:      ns.Name,
			Status:    string(ns.Status.Phase),
			Age:       formatAgeSince(ns.CreationTimestamp.Time),
			Created:   ns.CreationTimestamp.Time,
			Labels:    labelsToKV(ns.Labels),
		})
	}
	return result, nil
}

// ListPods 调 CoreV1().Pods(ns).List() 转 DTO. namespace 空返所有.
func (c *InClusterClient) ListPods(ctx context.Context, namespace string) ([]types.Pod, error) {
	list, err := c.clientset.CoreV1().Pods(namespace).List(ctx, metav1.ListOptions{})
	if err != nil {
		return nil, fmt.Errorf("列出 pod 失败 (ns=%s): %w", namespace, err)
	}

	result := make([]types.Pod, 0, len(list.Items))
	for _, p := range list.Items {
		ready, restarts := podReadyAndRestarts(&p)
		result = append(result, types.Pod{
			Name:      p.Name,
			Namespace: p.Namespace,
			Phase:     string(p.Status.Phase),
			Ready:     ready,
			Restarts:  restarts,
			Node:      p.Spec.NodeName,
			IP:        p.Status.PodIP,
			Age:       formatAgeSince(p.CreationTimestamp.Time),
			Created:   p.CreationTimestamp.Time,
		})
	}
	return result, nil
}

// ListDeployments 调 AppsV1().Deployments(ns).List() 转 DTO.
func (c *InClusterClient) ListDeployments(ctx context.Context, namespace string) ([]types.Deployment, error) {
	list, err := c.clientset.AppsV1().Deployments(namespace).List(ctx, metav1.ListOptions{})
	if err != nil {
		return nil, fmt.Errorf("列出 deployment 失败 (ns=%s): %w", namespace, err)
	}

	result := make([]types.Deployment, 0, len(list.Items))
	for _, d := range list.Items {
		image := ""
		if len(d.Spec.Template.Spec.Containers) > 0 {
			image = d.Spec.Template.Spec.Containers[0].Image
		}
		result = append(result, types.Deployment{
			Name:            d.Name,
			Namespace:       d.Namespace,
			Replicas:        ptrToInt32(d.Spec.Replicas),
			ReadyReplicas:   d.Status.ReadyReplicas,
			Available:       d.Status.AvailableReplicas,
			UpdatedReplicas: d.Status.UpdatedReplicas,
			Image:           image,
			Age:             formatAgeSince(d.CreationTimestamp.Time),
			Created:         d.CreationTimestamp.Time,
		})
	}
	return result, nil
}

// ClusterInfo 调 Discovery().ServerVersion() 拿版本, nodeName 已存.
func (c *InClusterClient) ClusterInfo(ctx context.Context) (version string, nodeName string, err error) {
	info, err := c.clientset.Discovery().ServerVersion()
	if err != nil {
		return "", c.nodeName, fmt.Errorf("拿 cluster version 失败: %w", err)
	}
	return info.GitVersion, c.nodeName, nil
}

// NodeName 返 worker 跑在哪个节点 (k8s downward API 注入的 env).
func (c *InClusterClient) NodeName() string {
	return c.nodeName
}
