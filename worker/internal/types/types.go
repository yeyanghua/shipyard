// Package types 定义 worker 内部共享类型 + shipyard ↔ worker 通信 DTO.
package types

import "time"

// ============================================================
// Cluster info (M8.1 返 mock,M8.3 切 client-go 返真数据)
// ============================================================

// Namespace 简化的 ns 描述.
type Namespace struct {
	Name    string    `json:"name"`
	Status  string    `json:"status"` // Active / Terminating
	Age     string    `json:"age"`
	Labels  []string  `json:"labels,omitempty"` // key=value
	Created time.Time `json:"createdAt"`
}

// Pod 简化的 pod 描述.
type Pod struct {
	Name      string    `json:"name"`
	Namespace string    `json:"namespace"`
	Phase     string    `json:"phase"` // Running / Pending / Failed / Succeeded / Unknown
	Ready     string    `json:"ready"` // "1/3" 形式
	Restarts  int32     `json:"restarts"`
	Node      string    `json:"node,omitempty"`
	IP        string    `json:"ip,omitempty"`
	Age       string    `json:"age"`
	Created   time.Time `json:"createdAt"`
}

// Deployment 简化的 deployment 描述.
type Deployment struct {
	Name            string    `json:"name"`
	Namespace       string    `json:"namespace"`
	Replicas        int32     `json:"replicas"`        // 期望副本数
	ReadyReplicas   int32     `json:"readyReplicas"`   // 就绪副本数
	Available       int32     `json:"available"`       // 可用副本数
	UpdatedReplicas int32     `json:"updatedReplicas"` // 已更新副本数
	Image           string    `json:"image,omitempty"` // 第一个 container 的镜像
	Age             string    `json:"age"`
	Created         time.Time `json:"createdAt"`
}

// ============================================================
// Worker register / heartbeat
// ============================================================

// RegisterRequest worker 启动时主动注册到 shipyard.
type RegisterRequest struct {
	WorkerName  string `json:"workerName"`
	Env         string `json:"env"`         // dev / test / prod
	K8sVersion  string `json:"k8sVersion"`  // 集群版本 (e.g. v1.30.3)
	NodeName    string `json:"nodeName"`    // 跑的节点名
	WorkerURL   string `json:"workerUrl"`   // shipyard 调 worker 用的 URL
	WorkerToken string `json:"workerToken"` // HMAC token (跟 shipyard 共享)
	Version     string `json:"version"`     // worker 版本
}

// RegisterResponse shipyard 返回 worker ID + 下次心跳间隔.
//
// WorkerID 是 string 而不是 int64: shipyard 后端 Jackson 把雪花 ID (Long) 序列化成 String
// 防止 JS 19 位精度丢失, Go 端直接用 string 接 + 调 path 时转回.
type RegisterResponse struct {
	WorkerID             string `json:"workerId"`
	HeartbeatIntervalSec int    `json:"heartbeatIntervalSec"`
}

// HeartbeatRequest worker 定期上报.
type HeartbeatRequest struct {
	WorkerID     string `json:"workerId"`
	Status       string `json:"status"` // online / unhealthy
	CPULoad      string `json:"cpuLoad,omitempty"`
	MemoryUsage  string `json:"memoryUsage,omitempty"`
	PodsCount    int    `json:"podsCount,omitempty"`

	// M9 commit-8: worker 自报健康状态
	// HEALTHY (默认) — worker 自检通过, 愿意接 deploy
	// UNHEALTHY — 自检失败 (k8s API / mem / disk), 不派活但继续心跳
	Health string `json:"health,omitempty"`

	// M9 commit-8: 自检失败原因 — 例 "k8s API timeout 3s" / "disk 95% > 90%"
	HealthDetail string `json:"healthDetail,omitempty"`
}

// HealthStatus 独立的 health 状态结构 — 给 RegisterHandler.HealthFn 注入用.
//
// 跟 HeartbeatRequest 的 health/healthDetail 字段语义一致, 拆出来避免 handler 依赖整个 Request DTO.
type HealthStatus struct {
	Health string // "HEALTHY" / "UNHEALTHY"
	Detail string // 失败原因 (HEALTHY 时为空)
}

// ============================================================
// Tasks (M8.4+)
// ============================================================

// EchoRequest M8.1 测试用 — shipyard 调过来验证通信.
type EchoRequest struct {
	Message   string                 `json:"message"`
	Timestamp int64                  `json:"timestamp"`
	Extra     map[string]interface{} `json:"extra,omitempty"`
}

// EchoResponse 原样返回 + worker 标识.
type EchoResponse struct {
	WorkerName  string      `json:"workerName"`
	WorkerID    string      `json:"workerId,omitempty"`
	Received    EchoRequest `json:"received"`
	ProcessedAt time.Time   `json:"processedAt"`
}

// ============================================================
// Deploy tasks (M9 commit-8) — shipyard → worker 调 deploy/scale/manifest
// ============================================================

// DeployTaskRequest POST /api/v1/tasks/deploy — shipyard 渲染完 yaml 后发过来, worker 真 apply.
//
// namespace + yaml 是必填;resourceName 可选 (worker 端做 sanity check).
// 跟 commit-5 shipyard 端 DeployRequest 对齐.
type DeployTaskRequest struct {
	Namespace    string `json:"namespace" binding:"required"`
	YAML         string `json:"yaml" binding:"required"`
	ResourceName string `json:"resourceName,omitempty"`
	DeployID     int64  `json:"deployRecordId,omitempty"` // shipyard 端 deploy_record.id, log 用
}

// DeployTaskResponse 返 {phase, message, manifest} — shipyard 端写到 deploy_record.error_message 用.
type DeployTaskResponse struct {
	Phase    string `json:"phase"`    // created / updated / rolled-back / unchanged / failed
	Message  string `json:"message"`
	Manifest string `json:"manifest,omitempty"` // k8s 真生效的 yaml, 高级模式 diff 用
}

// ScaleTaskRequest POST /api/v1/tasks/scale — 修改副本数.
type ScaleTaskRequest struct {
	Namespace string `json:"namespace" binding:"required"`
	Kind      string `json:"kind" binding:"required"` // Deployment / StatefulSet
	Name      string `json:"name" binding:"required"`
	Replicas  int    `json:"replicas"`
}

// ScaleTaskResponse 返 phase + message.
type ScaleTaskResponse struct {
	Phase   string `json:"phase"` // scaled / failed
	Message string `json:"message"`
}

// ManifestTaskResponse GET /api/v1/tasks/manifest — 返 raw yaml 字符串, 高级模式 diff 用.
type ManifestTaskResponse struct {
	Kind      string `json:"kind"`
	Name      string `json:"name"`
	Namespace string `json:"namespace"`
	Manifest  string `json:"manifest"`
}
