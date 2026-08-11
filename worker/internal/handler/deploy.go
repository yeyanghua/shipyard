// Package handler - deploy.go worker 接 shipyard 调的真 deploy 端点 (M9 commit-8).
//
// 3 端点:
//   POST /api/v1/tasks/deploy      真 apply yaml 到 k8s (DynamicClient + unstructured)
//   POST /api/v1/tasks/scale       改副本数 (typed client Update)
//   GET  /api/v1/tasks/manifest    拿 k8s 真生效 manifest (高级模式 diff 用)
//
// 跟 shipyard 端 WorkerClient 5 方法对齐 (commit-5):
//   - shipyard workerClient.deploy       → worker handler.deploy
//   - shipyard workerClient.rollback     → worker handler.deploy (复用, shipyard 端拿历史 yaml 重发)
//   - shipyard workerClient.scale        → worker handler.scale
//   - shipyard workerClient.stop         → worker handler.scale (replicas=0)
//   - shipyard workerClient.getManifest  → worker handler.getManifest
package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"

	"github.com/yeyanghua/shipyard/worker/internal/k8sclient"
	"github.com/yeyanghua/shipyard/worker/internal/types"
)

// DeployHandler M9 commit-8 新建 — 接 shipyard 调的真 deploy 写动作.
type DeployHandler struct {
	logger *zap.Logger
	k8s    k8sclient.K8sClient
}

// NewDeployHandler 创建 deploy handler. K8sClient 必传 (不为 nil).
func NewDeployHandler(logger *zap.Logger, k8s k8sclient.K8sClient) *DeployHandler {
	return &DeployHandler{logger: logger, k8s: k8s}
}

// Deploy POST /api/v1/tasks/deploy — shipyard 调真 apply yaml 到 k8s.
//
// @param DeployTaskRequest (namespace, yaml, resourceName, deployRecordId)
// @return DeployTaskResponse (phase, message, manifest)
func (h *DeployHandler) Deploy(c *gin.Context) {
	var req types.DeployTaskRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.Warn("Deploy 请求体解析失败", zap.Error(err))
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    400,
			"message": "请求体格式错误: " + err.Error(),
		})
		return
	}

	if req.Namespace == "" || req.YAML == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    400,
			"message": "namespace 和 yaml 必填",
		})
		return
	}

	h.logger.Info("Deploy start",
		zap.String("namespace", req.Namespace),
		zap.String("resourceName", req.ResourceName),
		zap.Int64("deployRecordId", req.DeployID),
	)

	phase, message, manifest, err := h.k8s.Apply(c.Request.Context(), req.Namespace, req.YAML)
	if err != nil {
		h.logger.Error("Deploy 失败", zap.Error(err), zap.String("namespace", req.Namespace))
		c.JSON(http.StatusInternalServerError, gin.H{
			"code":    500,
			"message": "k8s apply 失败: " + err.Error(),
			"data": gin.H{
				"phase":   "failed",
				"message": message,
			},
		})
		return
	}

	h.logger.Info("Deploy SUCCESS",
		zap.String("phase", phase),
		zap.String("namespace", req.Namespace),
		zap.String("message", message),
	)
	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "ok",
		"data": types.DeployTaskResponse{
			Phase:    phase,
			Message:  message,
			Manifest: manifest,
		},
	})
}

// Scale POST /api/v1/tasks/scale — shipyard 调改副本数.
//
// @param ScaleTaskRequest (namespace, kind, name, replicas)
// @return ScaleTaskResponse (phase, message)
func (h *DeployHandler) Scale(c *gin.Context) {
	var req types.ScaleTaskRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.Warn("Scale 请求体解析失败", zap.Error(err))
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    400,
			"message": "请求体格式错误: " + err.Error(),
		})
		return
	}

	if req.Namespace == "" || req.Kind == "" || req.Name == "" || req.Replicas < 0 {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    400,
			"message": "namespace / kind / name 必填, replicas 必须 >= 0",
		})
		return
	}

	h.logger.Info("Scale start",
		zap.String("namespace", req.Namespace),
		zap.String("kind", req.Kind),
		zap.String("name", req.Name),
		zap.Int("replicas", req.Replicas),
	)

	phase, message, err := h.k8s.Scale(c.Request.Context(),
		req.Namespace, req.Kind, req.Name, req.Replicas)
	if err != nil {
		h.logger.Error("Scale 失败", zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"code":    500,
			"message": "k8s scale 失败: " + err.Error(),
			"data": gin.H{
				"phase":   "failed",
				"message": message,
			},
		})
		return
	}

	h.logger.Info("Scale SUCCESS",
		zap.String("phase", phase),
		zap.String("namespace", req.Namespace),
		zap.String("kind", req.Kind),
		zap.String("name", req.Name),
		zap.Int("replicas", req.Replicas),
	)
	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "ok",
		"data": types.ScaleTaskResponse{
			Phase:   phase,
			Message: message,
		},
	})
}

// GetManifest GET /api/v1/tasks/manifest — 拿 k8s 真生效 manifest (高级模式 diff 用).
//
// @param kind, name, namespace 走 query string
// @return raw yaml 字符串 (不是 {code, message, data} 包装, shipyard 端透传)
func (h *DeployHandler) GetManifest(c *gin.Context) {
	kind := c.Query("kind")
	name := c.Query("name")
	namespace := c.Query("namespace")

	if kind == "" || name == "" || namespace == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    400,
			"message": "kind / name / namespace 必填 (query string)",
		})
		return
	}

	h.logger.Debug("GetManifest",
		zap.String("kind", kind),
		zap.String("name", name),
		zap.String("namespace", namespace),
	)

	manifest, err := h.k8s.GetManifest(c.Request.Context(), namespace, kind, name)
	if err != nil {
		h.logger.Error("GetManifest 失败", zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"code":    500,
			"message": "k8s get manifest 失败: " + err.Error(),
		})
		return
	}

	// shipyard 端直接吃 raw yaml 字符串 (高级模式 diff 组件), 不包 {code, message, data}
	c.Data(http.StatusOK, "application/yaml; charset=utf-8", []byte(manifest))
}
