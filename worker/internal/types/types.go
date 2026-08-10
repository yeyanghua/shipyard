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
type RegisterResponse struct {
	WorkerID             int64 `json:"workerId"`
	HeartbeatIntervalSec int   `json:"heartbeatIntervalSec"`
}

// HeartbeatRequest worker 定期上报.
type HeartbeatRequest struct {
	WorkerID    int64  `json:"workerId"`
	Status      string `json:"status"` // online / unhealthy
	CPULoad     string `json:"cpuLoad,omitempty"`
	MemoryUsage string `json:"memoryUsage,omitempty"`
	PodsCount   int    `json:"podsCount,omitempty"`
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
	WorkerID    int64       `json:"workerId,omitempty"`
	Received    EchoRequest `json:"received"`
	ProcessedAt time.Time   `json:"processedAt"`
}
