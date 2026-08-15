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
	"github.com/yeyanghua/shipyard/worker/internal/health"
	"github.com/yeyanghua/shipyard/worker/internal/k8sclient"
	"github.com/yeyanghua/shipyard/worker/internal/log"
	"github.com/yeyanghua/shipyard/worker/internal/server"
	"github.com/yeyanghua/shipyard/worker/internal/types"

	"k8s.io/client-go/kubernetes"
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
	// M8.3c fix: 加 5s timeout, 避免 token/ca.crt/apiserver 慢响应卡住 main 启动
	//    (HTTP server 没起 → readiness probe fail → pod 死循环重启)
	k8sCtx, k8sCancel := context.WithTimeout(context.Background(), 5*time.Second)
	k8s, k8sMode := initK8sClientWithCtx(cfg, logger, k8sCtx)
	k8sCancel()

	// 4. 拿集群信息 (用 timeout context, 5s 内拿不到走 unknown, 不卡 main)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	var k8sVer, nodeName string
	if k8s != nil {
		infoCtx, infoCancel := context.WithTimeout(ctx, 5*time.Second)
		v, n, err := k8s.ClusterInfo(infoCtx)
		infoCancel()
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
	// M9 commit-16: 传 clientset 给 ClusterHandler (ListWorkerPods 端点需要直接调 K8s API)
	var clusterClientset kubernetes.Interface
	if realK8s, ok := k8s.(*k8sclient.InClusterClient); ok {
		clusterClientset = realK8s.Clientset()
	}
	cluster := handler.NewClusterHandler(logger, k8s, clusterClientset)
	healthH := handler.NewHealthHandler(logger, cfg.Version)
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

	// M9.5: 从 downward API 读 POD_NAME / POD_IP (k8s manifest env 注入)
	// shipyard 端按 (env_id, podName) 严格匹配预登记 row, 没设就 404.
	podName := os.Getenv("POD_NAME")
	if podName == "" {
		// dev mode fallback: 用 hostname (跟 M8.3c 旧逻辑一致, 防止本地没装 k8s 直接挂)
		podName = fmt.Sprintf("dev-%s", getHostname())
		logger.Warn("POD_NAME env var 未设, 走 dev fallback (M9.5 strict mode 强烈建议生产配 downward API)", zap.String("fallback", podName))
	}
	podIP := os.Getenv("POD_IP")  // 可选, register 上报 shipyard 记录

	registerCfg := handler.RegisterConfig{
		ShipyardURL:       cfg.ShipyardURL,
		PodName:           podName,
		Env:               cfg.Env,
		K8sVersion:        k8sVer,
		NodeName:          nodeName,
		PodIP:             podIP,
		WorkerURL:         workerURL,
		WorkerToken:       workerToken,
		Version:           cfg.Version,
		HeartbeatInterval: cfg.HeartbeatInterval,
	}

	// M9 commit-9: 装配 health checker, 注入到 register handler — 心跳时带上 health 字段.
	// k8sCheckFn 直接调 K8sClient.ListNamespaces, 通了算健康.
	healthCfg := health.Config{
		DiskThresholdPercent: cfg.HealthDiskThresholdPercent,
		MemMinAvailableMB:    cfg.HealthMemMinAvailableMB,
		K8sCheckTimeout:      time.Duration(cfg.HealthK8sCheckTimeoutMS) * time.Millisecond,
		CacheTTL:             time.Duration(cfg.HealthCacheTTLSec) * time.Second,
	}
	checker := health.NewChecker(logger, healthCfg, func(ctx context.Context) error {
		if k8s == nil {
			return fmt.Errorf("k8s client not initialized")
		}
		_, err := k8s.ListNamespaces(ctx)
		return err
	})
	registerCfg.HealthFn = func(ctx context.Context) types.HealthStatus {
		r := checker.Check(ctx)
		return types.HealthStatus{Health: r.Health, Detail: r.Detail}
	}

	register := handler.NewRegisterHandler(logger, registerCfg, echo)
	if err := register.Start(ctx); err != nil {
		// 注册失败不致命, worker 还能服务 HTTP, 后台 goroutine 自己重试
		logger.Warn("register Start 失败, 后台会重试", zap.Error(err))
	}
	defer register.Stop()

	// 7. HTTP server
	srv := server.New(cfg, logger, cluster, healthH, echo, deploy)

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

// initK8sClientWithCtx 初始化 K8sClient, 3 模式按优先级尝试, 失败 fallback fake.
//
// 跟原 initK8sClient 区别: 整个初始化包到 ctx (默认 5s) 里, 超时 fallback fake.
//
// 为什么: 仔哥 PC 端 kubeadm 集群, ServiceAccount token / ca.crt / apiserver
// 偶尔慢响应, 原来阻塞的 NewInClusterClient (走 rest.InClusterConfig +
// kubernetes.NewForConfig) 会卡 30s+, main 没起 HTTP server, readiness probe fail,
// pod 死循环重启 (M8.3c 现象).
//
// 模式选择:
//   - K8S_IN_CLUSTER=true → in-cluster (ServiceAccount) — 跑 k8s pod
//   - K8S_IN_CLUSTER=false + KUBECONFIG 存在 → kubeconfig — Mac/PC 本地
//   - 都不行 → fake — 纯本机开发
func initK8sClientWithCtx(cfg *config.Config, logger *zap.Logger, ctx context.Context) (k8sclient.K8sClient, string) {
	type result struct {
		client k8sclient.K8sClient
		mode   string
	}
	resCh := make(chan result, 1)

	go func() {
		// 模式 1: in-cluster
		if cfg.K8sInCluster {
			c, err := k8sclient.NewInClusterClient("")
			if err == nil {
				resCh <- result{c, "in-cluster"}
				return
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
				resCh <- result{c, "kubeconfig"}
				return
			}
			logger.Warn("kubeconfig 初始化失败, fallback fake", zap.Error(err))
		}

		// 模式 3: fake
		logger.Info("走 fake mode (无 K8S_IN_CLUSTER 也没 KUBECONFIG)")
		resCh <- result{k8sclient.NewFakeClient(), "fake"}
	}()

	select {
	case r := <-resCh:
		return r.client, r.mode
	case <-ctx.Done():
		logger.Warn("K8sClient 初始化超时, fallback fake (apiserver 慢响应?)", zap.Error(ctx.Err()))
		return k8sclient.NewFakeClient(), "fake-timeout"
	}
}
