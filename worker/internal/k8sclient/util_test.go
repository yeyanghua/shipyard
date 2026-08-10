package k8sclient

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func TestFormatAgeSince(t *testing.T) {
	now := time.Now()
	tests := []struct {
		name string
		ts   time.Time
		want string
	}{
		{"30s ago", now.Add(-30 * time.Second), "30s"},
		{"5m ago", now.Add(-5 * time.Minute), "5m"},
		{"2h ago", now.Add(-2 * time.Hour), "2h"},
		{"3d ago", now.Add(-3 * 24 * time.Hour), "3d"},
		{"just now", now, "0s"},
		{"future", now.Add(1 * time.Hour), "0s"}, // 时间异常兜底
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.want, formatAgeSince(tt.ts))
		})
	}
}
