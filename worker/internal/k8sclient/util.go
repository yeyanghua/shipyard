// Package k8sclient - 工具函数 (age 格式化, pod 状态, 标签转 KV).
package k8sclient

import (
	"fmt"
	"time"

	corev1 "k8s.io/api/core/v1"
)

// formatAgeSince 把时间戳渲染成 "2h" / "30m" / "5d" 格式.
// 未来时间兜底返 "0s" (时钟异常 / 跨时区不返负数).
func formatAgeSince(t time.Time) string {
	d := time.Since(t)
	if d < 0 {
		d = 0
	}
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

// podReadyAndRestarts 计算 pod ready 字符串 ("1/3") + 总重启次数.
func podReadyAndRestarts(p *corev1.Pod) (ready string, restarts int32) {
	totalContainers := len(p.Spec.Containers)
	readyContainers := 0
	for _, cs := range p.Status.ContainerStatuses {
		if cs.Ready {
			readyContainers++
		}
		restarts += cs.RestartCount
	}
	return fmt.Sprintf("%d/%d", readyContainers, totalContainers), restarts
}

// labelsToKV 把 map[string]string 转成 "key=value" 列表. UI 显示用.
func labelsToKV(labels map[string]string) []string {
	if len(labels) == 0 {
		return nil
	}
	result := make([]string, 0, len(labels))
	for k, v := range labels {
		result = append(result, fmt.Sprintf("%s=%s", k, v))
	}
	return result
}

// ptrToInt32 安全读 *int32, nil 返 0.
func ptrToInt32(p *int32) int32 {
	if p == nil {
		return 0
	}
	return *p
}
