// Package handler - cluster.go 集群读类端点, 调 K8sClient 拿真数据 (or fake).
package handler

import (
	"fmt"
	"net/http"
	"os"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"

	"github.com/yeyanghua/shipyard/worker/internal/k8sclient"
)

// ClusterHandler 集群读类端点 — M8.3 起调 K8sClient (in-cluster 或 fake) 替硬编码 mock.
//
// M9 commit-16: 加 ListWorkerPods — 返 worker deployment 的 pod 列表 + replicas,
// shipyard 用这个展示 "1 worker DB row 对应 N 个 K8s pod" 的真相.
type ClusterHandler struct {
	logger   *zap.Logger
	k8s      k8sclient.K8sClient
	clientset kubernetes.Interface // M9.16: 强类型,直接查 deployments/pods (K8sClient 抽象没暴露)
}

// NewClusterHandler 创建 handler. K8sClient 必传 (不为 nil). clientset 可选 (in-cluster mode 走 K8s API, fake mode nil).
func NewClusterHandler(logger *zap.Logger, k8s k8sclient.K8sClient, clientset kubernetes.Interface) *ClusterHandler {
	return &ClusterHandler{logger: logger, k8s: k8s, clientset: clientset}
}

// workerDeploymentName 找 worker 自己所属的 deployment. 默认 "shipyard-worker".
//
// M9.16 实现: 用 env var WORKER_DEPLOYMENT_NAME 覆盖 (k8s manifest 里可以设), fallback 到 "shipyard-worker".
const workerDeploymentName = "shipyard-worker"

func (h *ClusterHandler) workerDeployment() string {
	if v := os.Getenv("WORKER_DEPLOYMENT_NAME"); v != "" {
		return v
	}
	return workerDeploymentName
}

// PodInfo 简化的 pod 状态 (shipyard 用).
type PodInfo struct {
	Name      string `json:"name"`
	Namespace string `json:"namespace"`
	Node      string `json:"node,omitempty"`
	IP        string `json:"ip,omitempty"`
	Phase     string `json:"phase"`           // Running / Pending / Failed
	Ready     string `json:"ready,omitempty"` // "1/1"
	CreatedAt string `json:"createdAt,omitempty"`
}

// WorkerInfo 返给 shipyard 的 worker 完整信息 (replicas + pod 列表).
type WorkerInfo struct {
	WorkerName    string     `json:"workerName"`
	Namespace     string     `json:"namespace"`
	Replicas      int32      `json:"replicas"`       // deployment 期望副本数
	ReadyReplicas int32      `json:"readyReplicas"`  // deployment 当前 ready 数
	Pods          []PodInfo  `json:"pods"`
}

// ListNamespaces GET /api/v1/cluster/namespaces — 列出所有 namespace.
func (h *ClusterHandler) ListNamespaces(c *gin.Context) {
	h.logger.Debug("ListNamespaces called", zap.String("remote", c.ClientIP()))

	namespaces, err := h.k8s.ListNamespaces(c.Request.Context())
	if err != nil {
		h.logger.Error("ListNamespaces failed", zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"code":    500,
			"message": "k8s API 失败: " + err.Error(),
		})
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "ok",
		"data":    namespaces,
	})
}

// ListPods GET /api/v1/cluster/pods?namespace=xxx — 列出指定 ns 的 pod.
func (h *ClusterHandler) ListPods(c *gin.Context) {
	ns := c.DefaultQuery("namespace", "")
	h.logger.Debug("ListPods called", zap.String("namespace", ns))

	pods, err := h.k8s.ListPods(c.Request.Context(), ns)
	if err != nil {
		h.logger.Error("ListPods failed",
			zap.String("namespace", ns),
			zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"code":    500,
			"message": "k8s API 失败: " + err.Error(),
		})
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "ok",
		"data":    pods,
	})
}

// ListDeployments GET /api/v1/cluster/deployments?namespace=xxx — 列出指定 ns 的 deployment.
func (h *ClusterHandler) ListDeployments(c *gin.Context) {
	ns := c.DefaultQuery("namespace", "")
	h.logger.Debug("ListDeployments called", zap.String("namespace", ns))

	deployments, err := h.k8s.ListDeployments(c.Request.Context(), ns)
	if err != nil {
		h.logger.Error("ListDeployments failed",
			zap.String("namespace", ns),
			zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"code":    500,
			"message": "k8s API 失败: " + err.Error(),
		})
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "ok",
		"data":    deployments,
	})
}

// ListWorkerPods GET /api/v1/cluster/worker-pods — 返 worker 自己的 deployment 信息 + pod 列表.
//
// M9.16: 解决 "1 个 worker DB row 对应 N 个 k8s pod" 显示问题.
// 直接调 K8s clientset 查 deployment + pods (in-cluster 模式需要 clientset, fake 模式返 0 pod).
func (h *ClusterHandler) ListWorkerPods(c *gin.Context) {
	ctx := c.Request.Context()
	deploymentName := h.workerDeployment()
	// worker 自己所在的 namespace (M9 决策: shipyard 命名空间)
	ns := os.Getenv("WORKER_NAMESPACE")
	if ns == "" {
		ns = "shipyard"
	}

	info := WorkerInfo{
		WorkerName: deploymentName,
		Namespace:  ns,
		Pods:       []PodInfo{},
	}

	if h.clientset == nil {
		// fake mode (无 k8s 客户端) — 返空 list + 0 replicas
		h.logger.Debug("ListWorkerPods in fake mode, no clientset")
		c.JSON(http.StatusOK, gin.H{
			"code":    0,
			"message": "ok",
			"data":    info,
		})
		return
	}

	// 拿 deployment 状态 (期望副本 / ready)
	dep, err := h.clientset.AppsV1().Deployments(ns).Get(ctx, deploymentName, metav1.GetOptions{})
	if err != nil {
		h.logger.Warn("ListWorkerPods: deployment 查不到",
			zap.String("namespace", ns),
			zap.String("deployment", deploymentName),
			zap.Error(err))
	} else {
		info.Replicas = ptrToInt32(dep.Spec.Replicas)
		info.ReadyReplicas = dep.Status.ReadyReplicas
	}

	// 拿 pod 列表 (label selector app=shipyard-worker, 跟 manifest 对齐)
	podList, err := h.clientset.CoreV1().Pods(ns).List(ctx, metav1.ListOptions{
		LabelSelector: "app=shipyard-worker",
	})
	if err != nil {
		h.logger.Error("ListWorkerPods: pod list 失败",
			zap.String("namespace", ns),
			zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"code":    500,
			"message": "k8s API 失败: " + err.Error(),
		})
		return
	}

	for _, p := range podList.Items {
		ready, total := 0, 0
		for _, cs := range p.Status.ContainerStatuses {
			total++
			if cs.Ready {
				ready++
			}
		}
		info.Pods = append(info.Pods, PodInfo{
			Name:      p.Name,
			Namespace: p.Namespace,
			Node:      p.Spec.NodeName,
			IP:        p.Status.PodIP,
			Phase:     string(p.Status.Phase),
			Ready:     fmt.Sprintf("%d/%d", ready, total),
			CreatedAt: p.CreationTimestamp.Time.Format("2006-01-02 15:04:05"),
		})
	}

	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "ok",
		"data":    info,
	})
}

func ptrToInt32(p *int32) int32 {
	if p == nil {
		return 0
	}
	return *p
}
