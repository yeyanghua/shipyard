// Package handler - cluster.go 集群读类端点, 调 K8sClient 拿真数据 (or fake).
package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"

	"github.com/yeyanghua/shipyard/worker/internal/k8sclient"
)

// ClusterHandler 集群读类端点 — M8.3 起调 K8sClient (in-cluster 或 fake) 替硬编码 mock.
type ClusterHandler struct {
	logger *zap.Logger
	k8s    k8sclient.K8sClient
}

// NewClusterHandler 创建 handler. K8sClient 必传 (不为 nil).
func NewClusterHandler(logger *zap.Logger, k8s k8sclient.K8sClient) *ClusterHandler {
	return &ClusterHandler{logger: logger, k8s: k8s}
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
