# shipyard — 部署版本清单 (M5 demo 截止 2026-08-09)

> **TL;DR**: V1 主体 (M1-M5) 已完成, M5 demo 端到端跑通 (浏览器实测). 当前 commit `f653d19`.
> M6-M15 待做. **真实 drone / k8s / Harbor 部署放到 M15**, 现在只起 MySQL + Redis + shipyard 自身.

---

## 1. 当前可立即部署 (3 个组件)

### 1.1 MySQL 8 — 已有 docker-compose.yml (M1 stub)

```bash
cd shipyard
docker compose up -d mysql
# 健康检查: docker exec shipyard-mysql mysqladmin ping -h localhost -uroot -prootpass
```

- 镜像: `mysql:8.0` (实际跑 `8.4.5` 也兼容)
- 默认账号: `root / rootpass`, 库: `shipyard`
- 端口: `3306` (改 MYSQL_PORT env var)
- 数据卷: `shipyard-mysql-data` (named volume, 持久化)
- 健康检查: `mysqladmin ping` 每 10s, 30s 启动缓冲
- 13 张表自动落: Flyway V1__init.sql 启动时跑

### 1.2 Redis 7 — 已有 docker-compose.yml

```bash
docker compose up -d redis
# 健康检查: docker exec shipyard-redis redis-cli -a redispass ping
```

- 镜像: `redis:7-alpine`
- 默认密码: `redispass`, 端口 `6379`
- AOF 持久化 (appendonly yes)
- 内存上限 128MB, LRU 淘汰

### 1.3 shipyard 后端 — 暂无 Dockerfile, 直接 java -jar

**当前状态**: M2 后端骨架已能跑 (commit `063970a`), M5 加了 build/env_var/drone 集成 + SSE.
**没有 Dockerfile** (M2 留了位置, 等 M7 真正做 Dockerfile 模板时统一补).
**M5 demo 阶段直接用 mvn 跑**:

```bash
# D 盘 / Linux 都行
cd shipyard
export MYSQL_PASSWORD=shipyard  # 或 PowerShell: $env:MYSQL_PASSWORD='shipyard'
mvn spring-boot:run
# 启动 2.5s, /actuator/health 返 UP
```

**生产建议**: 
- 短期 V1 demo: 跟 M1 stub 一样走 `mvn package -DskipTests` 出 `target/shipyard-0.1.0.jar` + `java -jar` 跑
- 长期 V1.5: 写 Dockerfile (maven:3.9-eclipse-temurin-21 AS build → eclipse-temurin:21-jre AS runtime) 推到 Harbor, k8s 拉

### 1.4 shipyard Web 前端 — 暂无 Dockerfile, pnpm build 出静态

**当前状态**: M3 骨架 + M4 对接 + M5 BuildDetail 实时 UI.
**没有 Dockerfile** (等 M7 补).

```bash
cd web
pnpm install
pnpm build   # 产物: web/dist/ (静态文件)
# 部署: nginx 1.27 / caddy 1.0 反代
```

**nginx 最小配置示例** (dev 验证够用):
```nginx
server {
  listen 80;
  root /path/to/web/dist;
  location / {
    try_files $uri $uri/ /index.html;
  }
  location /api/ {
    proxy_pass http://localhost:8080;  # shipyard 后端
    proxy_set_header Host $host;
  }
  # SSE 端点要关 proxy buffering!
  location /api/builds/*/stream {
    proxy_pass http://localhost:8080;
    proxy_buffering off;
    proxy_cache off;
    proxy_set_header Connection '';
    proxy_http_version 1.1;
  }
}
```

> **SSE 注意**: Vite dev proxy 跟 SSE 有兼容问题 (web/src/api/client.ts `sseBaseURL`), 生产 nginx + 8080 直连没事. 已记 memory.

---

## 2. 完整版本依赖 (M5 demo 当前)

### 2.1 后端 shipyard/

| 组件 | 版本 | 来源 |
|---|---|---|
| JDK | **21.0.6** LTS | Eclipse Temurin 推荐 |
| Maven | 3.8.9 | pom.xml 父 Spring Boot 3.2.5 |
| Spring Boot | **3.2.5** | pom.xml |
| MyBatis-Plus | 3.5.5 | mybatis-plus-spring-boot3-starter |
| Flyway | 9.22.3 | flyway-core + flyway-mysql |
| MySQL Connector | 8.3.0 | mysql-connector-j |
| Spring Security | 6.2.4 (Boot 引入) | ⚠️ **已知 demo-mode bug** (docs/KNOWN_ISSUES.md KI-001) |
| JJWT | 0.12.5 | jjwt-api + impl + jackson |
| Lombok | (Spring Boot 引入) | pom 配置 |
| Validation | (Spring Boot 引入) | jakarta.validation |
| Spotless | 2.43.0 | palantir-java-format (跟项目风格) |
| JUnit 5 | (Spring Boot Test 引入) | |
| AssertJ | (Spring Boot Test 引入) | |

**关键能力**:
- Java 21 虚拟线程 (Project Loom): `VirtualThreadConfig` 4 处启用 (Tomcat/MVC/@Async/命名 thread pool)
- AES-256-GCM 加密 (env_variable.value / project.repo_token / env.worker_token)
- HMAC-SHA256 webhook 验签 (drone)
- SSE 实时日志 (`text/event-stream`)
- Flyway V1 自动跑 13 张表 (ai_interaction / alert_log / build_log / build_record / deploy_record / dockerfile_template / env / env_variable / pipeline_template / project / project_dockerfile / project_env / worker)

### 2.2 前端 web/

| 组件 | 版本 | 来源 |
|---|---|---|
| Node.js | **22.13.0** LTS | nvm 切 |
| pnpm | **11.20.0** | (npm i -g pnpm) |
| Vue | 3.4 |  |
| TypeScript | 5.5 |  |
| Vite | 5.4 |  |
| Pinia | 2.2 | 状态管理 |
| Vue Router | 4 |  |
| Axios | 1.7 | (拦截器 unwrap body.data) |
| Vitest | 2.0 | 单测 |
| happy-dom | (vitest 引入) | DOM 环境 |

**关键设计**:
- 业务码体系: HTTP 永远 200, `body.code` 判断 (0=成功, 4xx/5xx 业务码)
- 雪花 ID 全局 String 序列化: 后端 Jackson `Long → ToStringSerializer` (防 19 位精度丢失)
- SSE 不走 Vite proxy: `sseBaseURL=http://localhost:8080` 直连
- Vite dev proxy: `/api → http://localhost:8080` (axios 走 proxy, EventSource 走直连)
- CORS 允许: `http://localhost:5173-5180` + 8080 + 3000 (dev 端口 fallback 范围)

### 2.3 基础设施 (本地 D 盘验证用)

| 组件 | 版本 | 备注 |
|---|---|---|
| MySQL | **8.4.5** | 服务 `MySQL84`, root/123456 |
| Redis | 7+ | 6379, 无密码 |
| OS | Windows 11 | PowerShell 5.1 |

---

## 3. 配置文件 (env / application.yml 关键项)

### 3.1 shipyard/src/main/resources/application.yml 关键 env

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/shipyard?...
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:123456}   # ⚠️ 默认是 dev 密码, 生产必改
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}

shipyard:
  crypto:
    key: ${SHIPYARD_CRYPTO_KEY:AAECAwQFBgcIC...}  # ⚠️ dev 默认 32 字节 Base64, 生产用 KMS
  security:
    demo-mode: true   # ⚠️ V1 demo 模式, 全 permitAll, V1.5 收紧
  drone:
    webhook-secret: ${SHIPYARD_DRONE_WEBHOOK_SECRET:dev-...}  # dev 默认, V1.5 真实 drone 改
    mock-enabled: true   # V1 demo mock drone, V1.5 改 false 接 RealDroneClient
    mock-step-delay-ms: 3000
  jwt:
    secret: ${SHIPYARD_JWT_SECRET:change-me-...}  # 256 bit, V1.5 改
    expiration-seconds: 86400
    issuer: shipyard
    whitelist:
      - /api/auth/**
      - /actuator/**
      - /webhook/drone
      - /api/health
      # ... 9 个
```

### 3.2 V1 已知问题 / 临时方案 (V1.5 必修)

详见 `docs/KNOWN_ISSUES.md`:
- **KI-001** Spring Security 6.2.4 + Boot 3.2.5 鉴权 lambda 不注册 → V1 走 `shipyard.security.demo-mode=true` (全 permitAll)
  - V1.5 修复: 升 Security 6.3 / 降 6.1 / 写自定义 AuthorizationFilter (3 选 1)
  - 影响: V1 demo 阶段无 JWT 也能调 API, 适合受控环境跑通

---

## 4. k8s 部署路线图 (M15 阶段做)

> 用户自己复习 k8s 顺手把 shipyard 部署上, **建议先看 M15 docker-compose.demo.yml** (M1 留的 stub 还没补), 或者直接写 k8s manifest.

### 4.1 M15 需要补的

1. **Dockerfile** (M2 留位置)
   - 后端: `shipyard/Dockerfile` (multi-stage: maven build + eclipse-temurin-21-jre runtime)
   - 前端: `web/Dockerfile` (node:22 build + nginx:1.27-alpine runtime)
   - worker: `worker/Dockerfile` (M8 写, golang:1.22 + scratch)
2. **k8s manifest** (`k8s/`)
   - `namespace.yaml` (shipyard)
   - `mysql-statefulset.yaml` (M15 用云 RDS 也行, 本地用 k8s StatefulSet)
   - `redis-deployment.yaml`
   - `shipyard-backend-deployment.yaml` (2 replicas + HPA)
   - `shipyard-web-deployment.yaml` (2 replicas)
   - `ingress.yaml` (nginx-ingress / traefik)
   - `secret.yaml` (MYSQL_PASSWORD / SHIPYARD_CRYPTO_KEY 等)
3. **drone CI** (V1.5 真正接)
   - 官方 k8s Helm chart: `helm repo add drone https://charts.drone.io`
   - shipyard → drone 走 API (建 repo secret)
   - drone → shipyard webhook 回调 (X-Drone-Signature HMAC)
4. **Harbor** (V1.5)
   - 官方 helm: `helm repo add harbor https://helm.goharbor.io`
   - shipyard worker 拉镜像用

### 4.2 不需要 (V1 demo 阶段不搭)

- ❌ k3s (用户改用 k8s, 不需要轻量替代)
- ❌ Prometheus + Grafana (M13 阶段)
- ❌ Alertmanager (M13)
- ❌ ArgoCD / Flux (M14 CI/CD 阶段)

---

## 5. 部署快速命令 (3 个组件先跑起来)

```bash
# 1. clone
git clone git@github.com:yeyanghua/shipyard.git
cd shipyard

# 2. 起 MySQL + Redis
docker compose up -d
# 等 30s 让 MySQL 初始化 + 13 张表自动落

# 3. 起后端
cd shipyard
export MYSQL_PASSWORD=shipyard
mvn spring-boot:run
# 等 25s, /actuator/health 返 UP

# 4. 起前端 (新 terminal)
cd web
pnpm install
pnpm dev
# Vite 5173 (或 fallback 5174-5180, 全在 CORS 白名单)
# 浏览器开 http://localhost:5173/

# 5. 走流程
# 项目页 + 新建项目 + 关联 env + 触发构建 + 看实时 SSE
```

---

## 6. 验证清单 (M5 demo 全部已过)

- [x] mvn test 23/23 (AesEncrypter 9 + HmacVerifier 14)
- [x] mvn spring-boot:run 启动 2.5s
- [x] /actuator/health UP
- [x] 13 张表 Flyway 自动落
- [x] POST /api/builds → mock drone 3 step → SUCCESS
- [x] SSE `/api/builds/{id}/stream` curl -N 收 4 事件
- [x] HMAC webhook 验签 (错签 401, 真签 200)
- [x] env vars 注入 drone (JAVA_HOME + DB_PASSWORD)
- [x] pnpm typecheck 0 errors
- [x] pnpm build 729ms
- [x] pnpm test 6/6
- [x] 浏览器 E2E 全流程 (创建 → 关联 → env vars → 触发 → SSE → 终态)

---

## 7. 联系 / 交接

- GitHub: https://github.com/yeyanghua/shipyard
- 当前 commit: `f653d19` (M5 demo + 4 bug 修复)
- 主分支: `main`
- 部署前必读: `docs/KNOWN_ISSUES.md` (鉴权 demo-mode 临时方案)
- 进度接力棒: `PROGRESS.md` (换设备无缝继续)
- 架构设计: `docs/superpowers/specs/2026-08-08-platform-design.md` (35KB)
- M15 计划: `docs/superpowers/plans/2026-08-08-platform-implementation.md` (16 milestone 路径)
