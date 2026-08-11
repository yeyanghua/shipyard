// Package k8sclient - in-cluster client 用 client-go 调真 k8s API (ServiceAccount 鉴权).
package k8sclient

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"

	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/runtime/schema"
	k8syaml "k8s.io/apimachinery/pkg/util/yaml"
	"k8s.io/client-go/dynamic"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/clientcmd"
	"sigs.k8s.io/yaml"

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
	restCfg   *rest.Config
	nodeName  string // 从 pod spec 读, 走 downward API

	// lazy init: DynamicClient 不像 Clientset 那样便宜 (起 background goroutines),
	// 用 once 保证只起一次, Apply / GetManifest 共享.
	dynOnce   sync.Once
	dynClient dynamic.Interface
	dynErr    error
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
		restCfg:   config,
		nodeName:  nodeName,
	}, nil
}

// dynamicClient 懒加载 DynamicClient — commit-7 Apply / GetManifest 用.
func (c *InClusterClient) dynamicClient() (dynamic.Interface, error) {
	c.dynOnce.Do(func() {
		c.dynClient, c.dynErr = dynamic.NewForConfig(c.restCfg)
	})
	return c.dynClient, c.dynErr
}

// gvrFromUnstructured 从 unstructured.Unstructured 拿 gvr (group/version/resource).
//
// apiVersion 格式:
//   - "v1"          → group="", version="v1"
//   - "apps/v1"      → group="apps", version="v1"
//   - "networking.k8s.io/v1" → group="networking.k8s.io", version="v1"
//
// kind → resource 映射 hardcode 5 个常见资源, 未知 kind 报错 (避免乱猜).
func gvrFromUnstructured(obj *unstructured.Unstructured) (schema.GroupVersionResource, error) {
	apiVersion := obj.GetAPIVersion()
	kind := obj.GetKind()

	// 拆 apiVersion
	parts := strings.SplitN(apiVersion, "/", 2)
	group, version := "", apiVersion
	if len(parts) == 2 {
		group = parts[0]
		version = parts[1]
	}

	// kind → resource (lowercase + 复数)
	resource := kindToResource(kind)
	if resource == "" {
		return schema.GroupVersionResource{}, fmt.Errorf("未知 kind: %s (V1 支持 5 个常见资源, 见 kindToResource)", kind)
	}

	return schema.GroupVersionResource{
		Group:    group,
		Version:  version,
		Resource: resource,
	}, nil
}

// gvrForKind 跟 gvrFromUnstructured 类似, 但只传 kind (GetManifest 用).
// V1 hardcode 5 个常见 kind, 未来扩 list.
func gvrForKind(kind string) (schema.GroupVersionResource, error) {
	known := map[string]schema.GroupVersionResource{
		"Deployment":  {Group: "apps", Version: "v1", Resource: "deployments"},
		"StatefulSet": {Group: "apps", Version: "v1", Resource: "statefulsets"},
		"Service":     {Group: "", Version: "v1", Resource: "services"},
		"ConfigMap":   {Group: "", Version: "v1", Resource: "configmaps"},
		"Secret":      {Group: "", Version: "v1", Resource: "secrets"},
	}
	if gvr, ok := known[kind]; ok {
		return gvr, nil
	}
	return schema.GroupVersionResource{}, fmt.Errorf("未知 kind: %s (V1 hardcode 5 个: Deployment/StatefulSet/Service/ConfigMap/Secret)", kind)
}

// kindToResource Kind → lowercase + 复数 (kubectl get 风格).
// V1 hardcode 5 个常见, 未知返空字符串 (gvrFromUnstructured 报错).
func kindToResource(kind string) string {
	known := map[string]string{
		"Deployment":  "deployments",
		"StatefulSet": "statefulsets",
		"DaemonSet":   "daemonsets",
		"Service":     "services",
		"ConfigMap":   "configmaps",
		"Secret":      "secrets",
	}
	return known[kind]
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

// ============================================================
// M9 commit-7: deploy 写动作 — DynamicClient + unstructured
// ============================================================

// Apply 把 yaml apply 到 k8s — 走 DynamicClient + unstructured (不写死 schema).
//
// 流程:
//  1. 用 k8s.io/apimachinery/pkg/util/yaml.NewYAMLOrJSONDecoder 解析 multi-doc yaml (--- 分隔)
//  2. 每个 doc 转 unstructured.Unstructured
//  3. 从 unstructured 拿 gvr (group/version/resource) — apiVersion + kind 决定
//  4. 拿 namespace (从 metadata.namespace 读, 缺省用参数 namespace)
//  5. 拿 name (从 metadata.name 读)
//  6. 查 Resource(gvr).Namespace(ns) 里 Get name — 存在 Update, 不存在 Create
//
// 返回 phase (created/updated/unchanged) + message + manifest (apply 后的真生效 yaml).
func (c *InClusterClient) Apply(ctx context.Context, namespace, yamlStr string) (string, string, string, error) {
	dc, err := c.dynamicClient()
	if err != nil {
		return "", "", "", fmt.Errorf("dynamic client 初始化失败: %w", err)
	}

	// 解析 multi-doc yaml
	decoder := k8syaml.NewYAMLOrJSONDecoder(bytes.NewReader([]byte(yamlStr)), 4096)
	var lastManifest strings.Builder
	createdCount, updatedCount, unchangedCount := 0, 0, 0
	overallMessage := ""

	for {
		var rawObj runtime.Object
		if err := decoder.Decode(&rawObj); err != nil {
			if errors.Is(err, io.EOF) {
				break
			}
			return "", "", lastManifest.String(), fmt.Errorf("yaml 解析失败: %w", err)
		}
		if rawObj == nil {
			break
		}

		// 转 unstructured
		obj, ok := rawObj.(*unstructured.Unstructured)
		if !ok {
			// 试转: runtime.Object 可能是 *unstructured.UnstructuredList 等
			uobj, err := runtime.DefaultUnstructuredConverter.ToUnstructured(rawObj)
			if err != nil {
				return "", "", lastManifest.String(), fmt.Errorf("转 unstructured 失败: %w", err)
			}
			obj = &unstructured.Unstructured{Object: uobj}
		}

		// 拿 gvr + namespace + name
		gvr, err := gvrFromUnstructured(obj)
		if err != nil {
			return "", "", lastManifest.String(), err
		}
		ns := obj.GetNamespace()
		if ns == "" {
			ns = namespace
		}
		if ns == "" {
			return "", "", lastManifest.String(), fmt.Errorf("resource 缺 namespace: kind=%s name=%s", obj.GetKind(), obj.GetName())
		}
		name := obj.GetName()
		if name == "" {
			return "", "", lastManifest.String(), fmt.Errorf("resource 缺 name: kind=%s", obj.GetKind())
		}

		// 查存在不
		resource := dc.Resource(gvr).Namespace(ns)
		existing, getErr := resource.Get(ctx, name, metav1.GetOptions{})
		var phase, message string
		if getErr != nil {
			// NotFound → Create, 其他错 → fail
			apierr := &apierrors.StatusError{}
			if errors.As(getErr, &apierr) && apierr.ErrStatus.Reason == metav1.StatusReasonNotFound {
				_, createErr := resource.Create(ctx, obj, metav1.CreateOptions{})
				if createErr != nil {
					return "", "", lastManifest.String(),
						fmt.Errorf("create %s/%s 失败: %w", gvr.Resource, name, createErr)
				}
				phase = "created"
				message = fmt.Sprintf("%s/%s created", gvr.Resource, name)
				createdCount++
			} else {
				return "", "", lastManifest.String(),
					fmt.Errorf("查 %s/%s 失败: %w", gvr.Resource, name, getErr)
			}
		} else {
			// 存在 → Update (用 existing resourceVersion 防冲突)
			obj.SetResourceVersion(existing.GetResourceVersion())
			_, updateErr := resource.Update(ctx, obj, metav1.UpdateOptions{})
			if updateErr != nil {
				return "", "", lastManifest.String(),
					fmt.Errorf("update %s/%s 失败: %w", gvr.Resource, name, updateErr)
			}
			phase = "updated"
			message = fmt.Sprintf("%s/%s updated", gvr.Resource, name)
			updatedCount++
		}

		// 拿 apply 后的 live manifest (用 yaml.Marshal 回写, 字段顺序跟 yaml 一致更好但 V1 简版)
		liveObj, getErr2 := resource.Get(ctx, name, metav1.GetOptions{})
		if getErr2 == nil {
			marshalled, mErr := yaml.Marshal(liveObj.Object)
			if mErr == nil {
				lastManifest.Reset()
				lastManifest.Write(marshalled)
				lastManifest.WriteString("\n---\n")
			}
		}

		overallMessage = message
		_ = phase
	}

	// 整体 phase
	totalPhase := "unchanged"
	if createdCount > 0 {
		totalPhase = "created"
	} else if updatedCount > 0 {
		totalPhase = "updated"
	}
	if totalPhase == "unchanged" && unchangedCount == 0 && createdCount == 0 && updatedCount == 0 {
		return "", "no resources to apply", "", fmt.Errorf("yaml 没解析出任何 K8s resource")
	}
	return totalPhase, overallMessage, lastManifest.String(), nil
}

// Rollback 接口跟 Apply 一样 (复用), 留单独方法为了未来扩展 (比如 kubectl rollout undo).
func (c *InClusterClient) Rollback(ctx context.Context, namespace, yamlStr string) (string, string, string, error) {
	return c.Apply(ctx, namespace, yamlStr)
}

// Scale 修改资源副本数. kind="Deployment" / "StatefulSet" / "ReplicaSet" 等.
//
// V1 简版: 直接 Update 整个 spec.replicas — 走 typed client (typed 比 DynamicClient 类型安全).
func (c *InClusterClient) Scale(ctx context.Context, namespace, kind, name string, replicas int) (string, string, error) {
	switch kind {
	case "Deployment":
		s, err := c.clientset.AppsV1().Deployments(namespace).Get(ctx, name, metav1.GetOptions{})
		if err != nil {
			return "", "", fmt.Errorf("Deployment/%s 查不到 (ns=%s): %w", name, namespace, err)
		}
		rep := int32(replicas)
		s.Spec.Replicas = &rep
		_, err = c.clientset.AppsV1().Deployments(namespace).Update(ctx, s, metav1.UpdateOptions{})
		if err != nil {
			return "", "", fmt.Errorf("Deployment/%s scale 失败: %w", name, err)
		}
		return "scaled", fmt.Sprintf("Deployment/%s scaled to %d", name, replicas), nil
	case "StatefulSet":
		s, err := c.clientset.AppsV1().StatefulSets(namespace).Get(ctx, name, metav1.GetOptions{})
		if err != nil {
			return "", "", fmt.Errorf("StatefulSet/%s 查不到 (ns=%s): %w", name, namespace, err)
		}
		rep := int32(replicas)
		s.Spec.Replicas = &rep
		_, err = c.clientset.AppsV1().StatefulSets(namespace).Update(ctx, s, metav1.UpdateOptions{})
		if err != nil {
			return "", "", fmt.Errorf("StatefulSet/%s scale 失败: %w", name, err)
		}
		return "scaled", fmt.Sprintf("StatefulSet/%s scaled to %d", name, replicas), nil
	default:
		return "", "", fmt.Errorf("scale kind=%s V1 不支持 (只支持 Deployment/StatefulSet)", kind)
	}
}

// GetManifest 拿 k8s 真生效的 spec — 走 DynamicClient 拿 unstructured, yaml.Marshal 返.
func (c *InClusterClient) GetManifest(ctx context.Context, namespace, kind, name string) (string, error) {
	dc, err := c.dynamicClient()
	if err != nil {
		return "", fmt.Errorf("dynamic client 初始化失败: %w", err)
	}

	gvr, err := gvrForKind(kind)
	if err != nil {
		return "", err
	}

	obj, err := dc.Resource(gvr).Namespace(namespace).Get(ctx, name, metav1.GetOptions{})
	if err != nil {
		return "", fmt.Errorf("get %s/%s 失败 (ns=%s): %w", kind, name, namespace, err)
	}

	marshalled, err := yaml.Marshal(obj.Object)
	if err != nil {
		return "", fmt.Errorf("manifest yaml 序列化失败: %w", err)
	}
	return string(marshalled), nil
}
