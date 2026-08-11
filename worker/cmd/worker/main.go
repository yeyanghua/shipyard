// shipyard worker 入口.
//
// 装配顺序:
//  1. 加载 config (env var)
//  2. 建 zap logger
//  3. 初始化 K8sClient (in-cluster / kubeconfig / fake, 3 模式自动 fallback)
//  4. 拿集群信息 (注册 shipyard 用)
//  5. wire handler (cluster / health / echo / register)
//  6. 启动 register goroutine (注册 + 30s 心跳)
//  7. 启动 gin HTTP server
//  8. 优雅关闭 (SIGINT/SIGTERM)
//
// 设计: cmd 极薄, 业务逻辑全在 internal/.
// 这样测试 (handler/cluster_test.go 等) 不需要跑 main 也能直接调 handler.
package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"go.uber.org/zap"

	"github.com/yeyanghua/shipyard/worker/internal/config"
	"github.com/yeyanghua/shipyard/worker/internal/handler"
	"github.com/yeyanghua/shipyard/worker/internal/k8sclient"
	"github.com/yeyanghua/shipyard/worker/internal/log"
	"github.com/yeyanghua/shipyard/worker/internal/server"
)

func main() {
	// 1. 加载配置
	cfg, err := config.Load()
	if err != nil {
		// 没有 logger, 用 stderr
		fmt.Fprintf(os.Stderr, "load config failed: %v\n", err)
		os.Exit(1)
	}

	// 2. logger
	logger := log.New(cfg.Env)
	defer func() { _ = logger.Sync() }()

	logger.Info("worker starting",
		zap.String("name", cfg.WorkerName),
		zap.String("env", cfg.Env),
		zap.String("version", cfg.Version),
		zap.Int("port", cfg.Port),
		zap.String("shipyard_url", cfg.ShipyardURL),
		zap.Bool("k8s_in_cluster", cfg.K8sInCluster),
		zap.String("kube_config_path", cfg.KubeConfigPath),
	)

	// 3. K8sClient — 3 模式: in-cluster (ServiceAccount) / kubeconfig (Mac 本地) / fake (无集群)
	//    任何模式失败都 fallback fake, 保证 worker 起来
	k8s, k8sMode := initK8sClient(cfg, logger)

	// 4. 拿集群信息
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	var k8sVer, nodeName string
	if k8s != nil {
		v, n, err := k8s.ClusterInfo(ctx)
		if err != nil {
			logger.Warn("ClusterInfo 失败, 走 unknown", zap.Error(err))
		} else {
			k8sVer = v
			nodeName = n
		}
	}
	logger.Info("K8sClient ready",
		zap.String("mode", k8sMode),
		zap.String("version", k8sVer),
		zap.String("node", nodeName),
	)

	// 5. wire handler
	cluster := handler.NewClusterHandler(logger, k8s)
	health := handler.NewHealthHandler(logger, cfg.Version)
	echo := handler.NewEchoHandler(logger, cfg.WorkerName)
	deploy := handler.NewDeployHandler(logger, k8s)  // M9 commit-8

	// 6. register handler (注册 + 心跳) — 独立于 HTTP server, 走后台 goroutine
	workerURL := os.Getenv("WORKER_URL")
	if workerURL == "" {
		// 默认: 同一台机器 + 监听端口 (shipyard 注册时回填)
		workerURL = fmt.Sprintf("http://localhost:%d", cfg.Port)
	}

	// WORKER_TOKEN: V1 demo 默认 "test-token" (V1.5 接真鉴权时改)
	// shipyard 后端 @NotBlank 强校验, 空字符串直接 400 拒.
	workerToken := os.Getenv("WORKER_TOKEN")
	if workerToken == "" {
		workerToken = "test-token"
		logger.Warn("WORKER_TOKEN 未设, 走 V1 demo 默认值 'test-token' (V1.5 接真鉴权后必填)")
	}

	registerCfg := handler.RegisterConfig{
		ShipyardURL:       cfg.ShipyardURL,
		WorkerName:        cfg.WorkerName,
		Env:               cfg.Env,
		K8sVersion:        k8sVer,
		NodeName:          nodeName,
		WorkerURL:         workerURL,
		WorkerToken:       workerToken,
		Version:           cfg.Version,
		HeartbeatInterval: cfg.HeartbeatInterval,
	}
	register := handler.NewRegisterHandler(logger, registerCfg, echo)
	if err := register.Start(ctx); err != nil {
		// 注册失败不致命, worker 还能服务 HTTP, 后台 goroutine 自己重试
		logger.Warn("register Start 失败, 后台会重试", zap.Error(err))
	}
	defer register.Stop()

	// 7. HTTP server
	srv := server.New(cfg, logger, cluster, health, echo, deploy)

	// 8. 优雅关闭 — SIGINT/SIGTERM 触发 cancel + Shutdown
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		sig := <-sigCh
		logger.Info("收到退出信号, 关闭 server", zap.String("signal", sig.String()))
		cancel()
		shutdownCtx, c := context.WithTimeout(context.Background(), 10*time.Second)
		defer c()
		if err := srv.Shutdown(shutdownCtx); err != nil {
			logger.Error("server shutdown 失败", zap.Error(err))
		}
	}()

	logger.Info("worker 启动中 ...")
	if err := srv.Start(); err != nil {
		logger.Error("server 启动失败", zap.Error(err))
		os.Exit(1)
	}
}

// initK8sClient 初始化 K8sClient, 3 模式按优先级尝试, 失败 fallback fake.
//
// 模式选择:
//   - K8S_IN_CLUSTER=true → in-cluster (ServiceAccount) — 跑 k8s pod
//   - K8S_IN_CLUSTER=false + KUBECONFIG 存在 → kubeconfig — Mac/PC 本地
//   - 都不行 → fake — 纯本机开发
func initK8sClient(cfg *config.Config, logger *zap.Logger) (k8sclient.K8sClient, string) {
	// 模式 1: in-cluster
	if cfg.K8sInCluster {
		c, err := k8sclient.NewInClusterClient("")
		if err == nil {
			return c, "in-cluster"
		}
		logger.Warn("in-cluster 初始化失败, fallback kubeconfig", zap.Error(err))
	}

	// 模式 2: kubeconfig (Mac/PC 本地)
	if cfg.KubeConfigPath != "" || os.Getenv("KUBECONFIG") != "" {
		path := cfg.KubeConfigPath
		if path == "" {
			path = os.Getenv("KUBECONFIG")
		}
		c, err := k8sclient.NewInClusterClient(path)
		if err == nil {
			return c, "kubeconfig"
		}
		logger.Warn("kubeconfig 初始化失败, fallback fake", zap.Error(err))
	}

	// 模式 3: fake
	logger.Info("走 fake mode (无 K8S_IN_CLUSTER 也没 KUBECONFIG)")
	return k8sclient.NewFakeClient(), "fake"
}
