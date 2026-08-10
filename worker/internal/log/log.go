// Package log 封装 zap logger,统一格式.
package log

import (
	"os"

	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

// New 创建生产/开发 logger.
// env = "production" -> JSON 结构化;其他 -> console (dev 友好).
func New(env string) *zap.Logger {
	var cfg zap.Config
	if env == "production" {
		cfg = zap.NewProductionConfig()
		cfg.EncoderConfig.TimeKey = "ts"
		cfg.EncoderConfig.EncodeTime = zapcore.ISO8601TimeEncoder
	} else {
		cfg = zap.NewDevelopmentConfig()
		cfg.EncoderConfig.EncodeLevel = zapcore.CapitalColorLevelEncoder
	}

	logger, err := cfg.Build()
	if err != nil {
		// zap 初始化失败兜底 — 退回 stderr 简单 logger
		os.Stderr.WriteString("failed to build zap logger: " + err.Error() + "\n")
		return zap.NewNop()
	}
	return logger
}
