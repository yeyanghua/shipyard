// Package handler 实现 worker HTTP 端点.
//
// M8.1 阶段: cluster.go 返 mock 数据,不调 k8s API.
// M8.3 阶段: 注入 K8sClient,调 client-go 返真数据 (interface 设计,见本文件最后).
package handler

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"

	"github.com/yeyanghua/shipyard/worker/internal/types"
)

// ClusterHandler 集群读类端点.
type ClusterHandler struct {
	logger *zap.Logger
	// k8s K8sClient // M8.3 注入, M8.1 留 nil,返 mock
}

// NewClusterHandler 创建 handler.
func NewClusterHandler(logger *zap.Logger) *ClusterHandler {
	return &ClusterHandler{logger: logger}
}

// ListNamespaces GET /api/v1/cluster/namespaces — 列出所有 namespace.
//
// M8.1 返 mock 3 个 ns;M8.3 切真数据.
func (h *ClusterHandler) ListNamespaces(c *gin.Context) {
	h.logger.Debug("ListNamespaces called",
		zap.String("remote", c.ClientIP()),
	)

	// TODO M8.3: if h.k8s != nil { return realData }
	// M8.1 mock:
	now := time.Now().Add(-24 * time.Hour)
	namespaces := []types.Namespace{
		{Name: "default", Status: "Active", Age: "30d", Created: now},
		{Name: "kube-system", Status: "Active", Age: "30d", Created: now},
		{Name: "shipyard", Status: "Active", Age: "1d", Created: time.Now().Add(-1 * time.Hour)},
	}

	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "ok",
		"data":    namespaces,
	})
}

// ListPods GET /api/v1/cluster/pods?namespace=xxx — 列出指定 ns 的 pod.
func (h *ClusterHandler) ListPods(c *gin.Context) {
	ns := c.DefaultQuery("namespace", "default")
	if ns == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    400,
			"message": "namespace query param required",
		})
		return
	}

	h.logger.Debug("ListPods called",
		zap.String("namespace", ns),
		zap.String("remote", c.ClientIP()),
	)

	// M8.1 mock:
	now := time.Now().Add(-2 * time.Hour)
	pods := []types.Pod{
		{
			Name:      "shipyard-backend-7d4f8b6c9-x2k7m",
			Namespace: ns,
			Phase:     "Running",
			Ready:     "1/1",
			Restarts:  0,
			Node:      "k3d-shipyard-server-0",
			IP:        "10.42.0.15",
			Age:       "2h",
			Created:   now,
		},
		{
			Name:      "shipyard-web-5b9c8d7f4-m8n3p",
			Namespace: ns,
			Phase:     "Running",
			Ready:     "1/1",
			Restarts:  0,
			Node:      "k3d-shipyard-server-0",
			IP:        "10.42.0.16",
			Age:       "2h",
			Created:   now,
		},
	}

	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "ok",
		"data":    pods,
	})
}

// ListDeployments GET /api/v1/cluster/deployments?namespace=xxx — 列出指定 ns 的 deployment.
func (h *ClusterHandler) ListDeployments(c *gin.Context) {
	ns := c.DefaultQuery("namespace", "default")
	if ns == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    400,
			"message": "namespace query param required",
		})
		return
	}

	h.logger.Debug("ListDeployments called",
		zap.String("namespace", ns),
		zap.String("remote", c.ClientIP()),
	)

	// M8.1 mock:
	now := time.Now().Add(-2 * time.Hour)
	deployments := []types.Deployment{
		{
			Name:            "shipyard-backend",
			Namespace:       ns,
			Replicas:        2,
			ReadyReplicas:   2,
			Available:       2,
			UpdatedReplicas: 2,
			Image:           "ghcr.io/yeyanghua/shipyard-backend:0.1.0",
			Age:             "2h",
			Created:         now,
		},
		{
			Name:            "shipyard-web",
			Namespace:       ns,
			Replicas:        1,
			ReadyReplicas:   1,
			Available:       1,
			UpdatedReplicas: 1,
			Image:           "ghcr.io/yeyanghua/shipyard-web:0.1.0",
			Age:             "2h",
			Created:         now,
		},
	}

	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "ok",
		"data":    deployments,
	})
}
