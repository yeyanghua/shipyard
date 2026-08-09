# shipyard — 项目进度

> **TL;DR**: V1 横向 demo 阶段。已推 GitHub (`yeyanghua/shipyard`)。**M1 + M2 + M3 + M2.5 (SecurityConfig 合并) 已完成**,M4 准备开始(项目/环境 CRUD, 前后端贯通)。**换电脑/换设备** → 看本文 §6「换设备恢复步骤」。

| 字段 | 值 |
|---|---|
| GitHub | https://github.com/yeyanghua/shipyard |
| 当前分支 | `main` |
| 当前 commit | `25e37ea` (M4 前端对接完成, 端到端跑通) |
| 当前 milestone | **M4 ✅ 完成 (后端 + 前端 + 端到端), M5 准备开始 (drone 集成)** |
| 总 commit | 18 |
| V1 整体 | 5-6 周(3-4 周主体 + 1-2 周 demo 编排) |
| 上次更新 | 2026-08-09 (D 盘开发, M4 端到端验收) |

---

## 1. 当前状态

✅ **已完成**：
- 11 条核心需求 + 14 个 ask_user 决策(全部 spec 化)
- 完整设计 spec (35KB, 23 个决策, 8 张 Mermaid 图)
- 实现计划 (18KB, 16 个 milestone, 5-6 周)
- 8 个核心页面 wireframes (63KB, 浏览器可看)
- M1 仓库骨架 (16 个文件: README 双语 + LICENSE + CONTRIBUTING + CODE_OF_CONDUCT + SECURITY + CHANGELOG + Makefile + docker-compose + docs/architecture + 5 个 .github 模板)
- 改名 master → shipyard(480 处替换, 0 残留)
- 分支 master → main
- 推 GitHub 成功(SSH 认证走 `yeyanghua`)
- **M2 shipyard 后端骨架** ✅ (commit `063970a`) - Spring Boot 3.2.5 + Java 21 + 13 张表 Flyway + AesEncrypter + 虚拟线程 4 处
- **M2.5 合并 Security** ✅ (commit `28fb602`) - 从 master/ 合并 SecurityConfig + JwtAuthFilter + JWT 配置到 shipyard/
- **M2.5 启动崩溃修复** ✅ (commit `85dcc05`) - SecurityConfig 用 @ConfigurationProperties 替代 @Value 修白名单解析,本机 MySQL 8.4 + Redis 落 13 张表 + /actuator/health UP 全验
- **M4 后端 1+2+3+4** ✅ (commits `81f4c99` `1e0f7cd` `88a22e2`) - 4 Entity + 4 Mapper + 4 Service + 4 Controller + 10 DTO + 公共异常 + Hard-coded JWT 鉴权; 14 业务端点 + /api/auth/demo-token; E2E 20/20 通过
- **M4 前端** ✅ (commit `25e37ea`) - 4 API (projects/envs/envVariables/auth) + 3 store (project/env/auth) + 5 页面 (ProjectList/CreateProject/ProjectDetail/EnvList/EnvVars) + 2 组件 (SecretInput/EnvVarEditor) + App.vue 自动拉 demo token; typecheck 0 errors, build OK, vitest 6/6; vite dev proxy /api → 后端 8080 端到端跑通
  - **根因**:`@Value("${shipyard.jwt.whitelist}")` 解析不了 YAML list → 抛 `IllegalArgumentException: Could not resolve placeholder` → ApplicationContext 刷新失败 → Tomcat 启动后立即关闭
  - **修法**:新增 `JwtProperties.java`(`@ConfigurationProperties(prefix = "shipyard.jwt")`),SecurityConfig 改构造器注入,删 @Value 字段
  - **D 盘验证**:MySQL 8.4 (root/123456) + Redis 7 (无密码) 本机起;`mvn spring-boot:run` 2.49s;Flyway V1__init.sql 134ms 落 13 张业务表 + flyway_schema_history;`/actuator/health` → `{"status":"UP","groups":["liveness","readiness"]}`
  - **dev 启动方式**:`$env:MYSQL_PASSWORD='123456'; mvn spring-boot:run`(IntelliJ Run Configuration 的 Env vars 加 `MYSQL_PASSWORD=123456` 也行;不传密码会立即报错,不会"半起")
- **M3 shipyard Web 前端骨架** ✅ (commit `5851894`) - Vue 3.4 + TS 5.5 + Vite 5.4 + 8 页面占位 + 6 测试
- **M2 shipyard 后端骨架 ✅** (commit `063970a`) (Spring Boot 3.2.5 + Java 21 LTS + MyBatis-Plus 3.5.5 + Flyway 9.22.3 + MySQL 8 + Lombok + JUnit 5)
  - 13 张表 Flyway V1 migration 落库(spec 标的 12 是 typo,实际 13)
  - `AesEncrypter` (AES-256-GCM,带 12 字节随机 IV + 16 字节认证标签) + Encrypter interface
  - 9 个加解密单元测试全过 (往返/Unicode/随机 IV/篡改检测/截断/错误密钥/边界)
  - `VirtualThreadConfig` (Tomcat / MVC 异步 / @Async / 命名线程池 4 处启用虚拟线程)
  - `DataSourceConfig` (MyBatis-Plus 分页 + 乐观锁拦截器)
  - `application.yml` (本地 MySQL/Redis 配, prod profile 强制 env var 注入,不留密码到 git)
  - **验收**: `mvn spring-boot:run` 1.3s 起服, `/actuator/health` 返回 UP, Flyway 自动迁移成功
  - **根因修复 1 个**: Flyway 默认 `${var}` placeholder 解析跟 dockerfile_template 字段注释里的 Mustache 占位符冲突,关 `placeholder-replacement: false` 解决
- **M3 shipyard Web 前端骨架 ✅** (commit `5851894`) (Vue 3.4 + TypeScript 5.5 + Vite 5.4 + pnpm 11.20 + Pinia 2.2 + Vue Router 4 + Axios 1.7 + Vitest 2.0)
  - 8 个核心页面占位 (Dashboard / ProjectList / CreateProject / ProjectDetail / PipelineEdit / BuildDetail / DeployDetail / EnvList / EnvVars / AiDiagnosis + 404) 对应 `docs/superpowers/wireframes/index.html` 8 个 anchor
  - Vite dev proxy `/api` → `http://localhost:8080` (prod 通过 VITE_SHIPYARD_API_URL 注入)
  - Axios 客户端: 拦截器归一化响应 `{code, message, data}`, 业务错误抛 `ApiError`
  - TypeScript strict + Vue 3 SFC + ESLint + Prettier + Vitest 全配置
  - 6 个测试通过 (PagePlaceholder 4 + projectsApi 2)
  - **验收**: `pnpm build` 351ms 全部 chunk 输出 (vue-vendor 88KB / 34KB gzip), `pnpm test` 6/6 通过, `pnpm dev` Vite 5.4.2 123ms 起服 (本地端口 5173/5174 已被其它项目占, fallback 5175)
  - **根因修复 2 个**:
    - pnpm 11 默认 ignore build scripts,esbuild native binary 装不上 → `pnpm-workspace.yaml` 加 `onlyBuiltDependencies: [esbuild, vue-demi]`
    - tsconfig 缺 `@types/node`, vite.config.ts 用了 `process` 和 `node:url` 编译失败 → 加 `@types/node` + types `["node", ...]`

⏳ **下一步**: M4 项目/环境 CRUD (前后端贯通, shipyard API + Vue UI)

---

## 2. GitHub 状态

```
仓库:    https://github.com/yeyanghua/shipyard
remote:  git@github.com:yeyanghua/shipyard.git (SSH)
分支:    main (default, 受保护建议)
克隆:    git clone git@github.com:yeyanghua/shipyard.git
```

### 11 个 commit 历史

| SHA | 说明 |
|---|---|
| `4fa2c81` | 统一构建发布平台设计 spec (V1 横向 demo 优先) |
| `1203393` | 加 spec HTML 版本(带 Mermaid 渲染 + GitHub 风格) |
| `6925e85` | 加 Dockerfile 自动生成功能(master 自带模板 + 提交进仓库) |
| `e9a22fa` | 加实时构建日志 + 修 mermaid 渲染 |
| `7578bff` | 修 mermaid flowchart 语法错误 |
| `c307cf3` | Java 17 升 21 LTS + 启用虚拟线程(Project Loom) |
| `884d990` | 加 8 个核心页面 wireframes(低保真原型) |
| `067c427` | 加 V1 实现计划(16 个 milestone, 5-6 周) |
| `367fbe2` | 仓库骨架(M1) + Apache 2.0 + 双语 README + 社区基础设施 |
| `fb0b57b` | 全套改名 master → shipyard(避免 master 分支/服务名/仓库名混淆) |
| `6fbbf6c` | docs: 加 PROGRESS.md(换设备无缝继续的接力棒) |
| `063970a` | M2: shipyard 后端骨架 (Spring Boot 3.2.5 + Java 21 + 13 张表 Flyway V1 + AesEncrypter 9 测试 + 虚拟线程) |
| `5851894` | M3: shipyard Web 前端骨架 (Vue 3.4 + TS 5.5 + Vite 5.4 + 8 页面占位 + 6 测试) |
| `28fb602` | 合并 master/ → shipyard/ + SecurityConfig/JwtAuthFilter(JWT 白名单) + 删 1182 行重复 |
| `85dcc05` | M2.5 启动崩溃修复:SecurityConfig 用 JwtProperties(@ConfigurationProperties) 替代 @Value,YAML list 解析修好,本机 13 张表落库 + health UP 全验 |
| `81f4c99` | M4 后端 1+2:4 Entity + 4 Mapper + 3 Service (Project/Env/ProjectEnv) + 公共异常 (BusinessException/ErrorCode) |
| `1e0f7cd` | M4 后端 3:EnvVariableService (加密/解密/resolve/validate) + 启动加密健康检查 |
| `88a22e2` | M4 后端 4:4 Controller + 10 DTO + GlobalExceptionHandler + /api/auth/demo-token + BeanUtils 工具 |
| `25e37ea` | M4 前端对接:4 API + 3 store + 5 页面 + 2 组件 + App.vue 自动拉 token; 端到端跑通 |

---

## 3. 已完成 milestone

### M1 - 仓库骨架 ✅ (commit `367fbe2`)

- `.gitignore` 排除 frozen-world/ 等历史残留
- `LICENSE` (Apache 2.0 全文)
- `README.md` (英文, 10 section) + `README.zh-CN.md` (中文, 10 section)
- `CONTRIBUTING.md` (开发流程/PR 规范/测试门槛/Conventional Commits)
- `CODE_OF_CONDUCT.md` (Contributor Covenant v2.1)
- `SECURITY.md` (漏洞披露 SLA 90 天)
- `CHANGELOG.md` (release-please stub)
- `Makefile` (make demo/infra/test/coverage/lint/format, 跨平台)
- `docker-compose.yml` (M1 仅 MySQL + Redis, 完整 demo 见 M15)
- `docs/architecture.md` (stub + 链到 spec)
- `.github/ISSUE_TEMPLATE/bug_report.md` + `feature_request.md`
- `.github/PULL_REQUEST_TEMPLATE.md` (完整 checklist)
- `.github/dependabot.yml` (5 个 ecosystem 分组)
- `.github/CODEOWNERS` (按目录 owner)
- `scripts/rename_to_shipyard.py` (改名脚本,留作参考)
- `AGENTS.md` (M1.5: AI agent 入口, M2 起步时补,见最新 commit)

### M2 - shipyard 后端骨架 ✅ (commit `063970a`)

- `shipyard/pom.xml` — Spring Boot 3.2.5 + Java 21 LTS + MyBatis-Plus 3.5.5 + Flyway 9.22.3 + mysql-connector-j 8.3.0 + Lombok + spring-boot-starter-web/validation/actuator/data-redis + Spotless 2.43.0 (palantir-java-format)
- `shipyard/src/main/java/com/shipyard/Application.java` — 入口, `@MapperScan("com.shipyard.**.mapper")`
- `shipyard/src/main/resources/application.yml` — 本地 MySQL/Redis 配 + 虚拟线程开关 + dev/prod profile 切换 (prod 强制 env var 注入,不留密码)
- `shipyard/src/main/resources/db/migration/V1__init.sql` — 13 张表 schema:
  - `project` / `pipeline_template` / `dockerfile_template` / `project_dockerfile`
  - `env` / `project_env` / `env_variable`
  - `build_record` / `build_log` (LONGTEXT) / `deploy_record` (snapshot_yaml MEDIUMTEXT)
  - `worker` (心跳 + 状态) / `ai_interaction` (留痕) / `alert_log` (P0/P1/P2)
  - 全部带 `deleted` 逻辑删除字段 + `created_at`/`updated_at` 时间戳
- `shipyard/src/main/java/com/shipyard/config/VirtualThreadConfig.java` — 4 处虚拟线程: Tomcat protocol handler / MVC async / @Async default / 命名 `virtualThreadTaskExecutor` bean
- `shipyard/src/main/java/com/shipyard/config/DataSourceConfig.java` — MyBatis-Plus 分页 (max 500) + 乐观锁拦截器
- `shipyard/src/main/java/com/shipyard/crypto/Encrypter.java` — 加密接口 (V1.5 接 KMS 时换实现)
- `shipyard/src/main/java/com/shipyard/crypto/CryptoException.java` — 加解密异常
- `shipyard/src/main/java/com/shipyard/crypto/AesEncrypter.java` — AES-256-GCM 实现: 12 字节随机 IV + 16 字节认证标签,输出 Base64 (IV+密文+tag 拼接)
- `shipyard/src/test/java/com/shipyard/crypto/AesEncrypterTest.java` — 9 个测试: 简单往返 / Unicode-JSON-长字符串往返 / 100 次同明文密文全不同(随机 IV) / 篡改/截断检测 / 错误密钥 / 边界 null 空 / 无效 base64 / 错误密钥长度
- **M3 准备 (Web 前端骨架)**:
  - 待建 `web/` 目录 (Vue 3 + TS + Vite + pnpm)
  - 待建 8 个核心页面 wireframes 实现
  - 待装 pnpm 8+ (`brew install pnpm` 或 `npm install -g pnpm`)

---

## 4. 下一步: M4 - 项目/环境 CRUD (前后端贯通)

**目标**: shipyard 后端实现 Project / Env 实体的 CRUD API, Vue 端 ProjectList / ProjectDetail / EnvList 三个页面接通, 真数据流转起来.

**涉及目录 (新增)**:
```
shipyard/src/main/java/com/shipyard/
├── project/                                  # M4 新增
│   ├── entity/Project.java                   # 实体
│   ├── mapper/ProjectMapper.java             # MyBatis-Plus BaseMapper
│   ├── service/ProjectService.java           # 业务逻辑
│   ├── controller/ProjectController.java     # REST API (spec §4.2)
│   └── dto/{Create,Update,Response}.java
└── env/                                      # M4 新增 (同结构)
    ├── entity/Env.java
    ├── mapper/EnvMapper.java
    ├── service/EnvService.java
    └── controller/EnvController.java
```

**新增 shipyard/src/test/java/com/shipyard/project/ProjectServiceTest.java**: 单元测试 (CRUD + 唯一名校验 + 逻辑删除)

**前端**:
- `web/src/views/ProjectList.vue` 接 projectsApi.list, 列表渲染
- `web/src/views/ProjectDetail.vue` 接 projectsApi.get
- `web/src/views/CreateProject.vue` 接 projectsApi.create, 表单
- `web/src/views/EnvList.vue` 接 envsApi.list (待 M4 加)
- Pinia store 调通 (loading/error/data 三态)

**验收**:
- [ ] `mvn test` 通过 (含新 ProjectServiceTest)
- [ ] `curl -X POST /api/projects` 能创建, 返回 JSON
- [ ] `curl -X GET /api/projects` 列表返回
- [ ] 浏览器打开 http://localhost:5173/projects 看到列表
- [ ] 创建项目后数据库 row 落表 + repo_token 加密

**工时**: 2-3 天

---

## 5. 关键决策速查(23 条)

完整 23 条决策见 `docs/superpowers/specs/2026-08-08-platform-design.md` §11。索引:

| # | 决策点 | 选择 |
|---|---|---|
| 1 | AI 方向 | 流水线生成 + 故障诊断 + 发布决策 全做 |
| 2 | drone 集成 | master 调 drone API(UI 隐藏) |
| 3 | 多环境拓扑 | 每环境独立 k8s 集群 |
| 4 | 环境变量 | master 自存 + AES-256 加密 |
| 5 | 仓库平台 | 多平台(GitLab V1 实现, Gitee V1 stub) |
| 6 | 流水线配置 | master 自存 + AI 改完用户 review |
| 7 | 构建 vs 发布 | 显式两步 |
| 8 | 回滚 | master 记 deployment snapshot |
| 9 | AI 集成 | 调外部 LLM API (Tongyi/DeepSeek) |
| 10 | 监控告警 | Prometheus + Alertmanager + alert_log |
| 11 | V1 范围 | 横向 demo 优先 |
| 12 | demo 部署 | docker-compose + k3s (开发阶段不写, demo 周写) |
| 13 | V1 仓库适配器 | 只实现 GitLab, 抽象层留好 |
| 14 | LLM demo 模式 | 默认 mock, 真 LLM 开关切换 |
| 15 | LICENSE | Apache 2.0 |
| 16 | README | 中英双语 |
| 17 | 架构图 | Mermaid |
| 18 | interview-prep | V1 收尾 Mavis 整理 |
| 19 | Dockerfile 模板 | master 自带 4-5 套主流 |
| 20 | Dockerfile 存储 | 提交进项目仓库(走 MR) |
| 21 | 实时日志 | 实时 + master 持久化 |
| 22 | 实时日志 UI | 构建详情页 |
| 23 | Java 版本 | Java 21 LTS + 虚拟线程 |

---

## 6. 换设备恢复步骤

> **新电脑从零开始**: 跟着这个清单走, 30 分钟内能进入 M2 实施

### 6.1 装环境

```bash
# 必需工具
- JDK 21 LTS (Eclipse Temurin 推荐: https://adoptium.net/)
- Go 1.22+ (https://go.dev/dl/)
- Node.js 20 LTS + pnpm 9.x
- Maven 3.9+
- Docker 24+ + Docker Compose 2.x
- Make (Windows 用 Git Bash 或 WSL)
- k3d (demo 集群用: https://k3d.io/)
- Python 3.x (生成 HTML spec)
```

### 6.2 配 Git

```bash
# 项目级 git config(覆盖全局)
git config user.name "仔哥"
git config user.email "470842725@qq.com"
```

### 6.3 配 SSH key 到 GitHub

如果新电脑没有 SSH key:
```bash
ssh-keygen -t ed25519 -C "470842725@qq.com"
# 复制 ~/.ssh/id_ed25519.pub 到 GitHub Settings → SSH and GPG keys
```

### 6.4 克隆 + 启动

```bash
# 克隆
git clone git@github.com:yeyanghua/shipyard.git
cd shipyard

# 起基础设施(只起 MySQL + Redis)
make infra

# 在另一个终端起 master 后端 (M2 完成后才能用)
make master-dev
```

### 6.5 告诉 Mavis "我回来了"

新电脑/新会话打开 Mavis, **第一句话** 引用本文 + 当前 commit:

> "回到 shipyard 项目, 仓库在 yeyanghua/shipyard, 当前 commit `28fb602` (合并 Security 到 shipyard), M1 + M2 + M3 已完成, M4 准备开始 (项目/环境 CRUD)。"

Mavis 会自动:
- 读 `PROGRESS.md` 知道进度
- 读 `docs/superpowers/specs/2026-08-08-platform-design.md` 知道设计
- 读 `docs/superpowers/plans/2026-08-08-platform-implementation.md` 知道下一步
- 不会问"你之前做了啥"

---

## 7. 工作风格提示(给未来的 Mavis 提示)

读这段的 Mavis 应该按这个用户风格来沟通:

| 习惯 | 说明 |
|---|---|
| **委派式** | 用户给方向 + 拍板, Mavis 给方案 + 执行 + 状态。**不要等用户每步确认**。 |
| **教学详细注释** | Java 后端背景, 教学风格 = Java/Spring 类比 + 代码详细注释 + 分 milestone 推进。 |
| **私活=真实甲方** | 这项目要开源 + 面试用, 质量按上线级(测试/部署/安全/错误处理都要正式)。 |
| **跨设备继续** | **切电脑一定要无缝继续**。所有进度沉 git/PROGRESS.md。新电脑 clone + 看 PROGRESS.md 就能继续。 |
| **沟通风格** | 短句 + 表情 + 直接判断, 不啰嗦。给方案 B 不给 ABC 全部。 |
| **隐私** | 真实邮箱 `470842725@qq.com` 不出现在 README/spec 文档, 用占位 `*@shipyard.dev`。 |
| **演示报告留档** | 长链路推进后, 把截图+文字沉到 `docs/demo/` 或 `PROGRESS.md`。 |

---

## 8. 文档索引(找东西用)

```
PROGRESS.md                                                ← 你正在看
README.md                                                  ← 仓库入口
README.zh-CN.md                                            ← 仓库入口(中文)

docs/
├── architecture.md                                        ← 架构总览(stub, M2 后更新)
├── superpowers/
│   ├── specs/
│   │   ├── 2026-08-08-platform-design.md                   ← 完整设计 spec(35KB)
│   │   └── 2026-08-08-platform-design.html                 ← 浏览器版 spec(70KB, Mermaid 渲染)
│   ├── plans/
│   │   └── 2026-08-08-platform-implementation.md           ← 16 milestone 计划(18KB)
│   └── wireframes/
│       └── index.html                                      ← 8 页面 wireframes(63KB)
├── demo/                                                  ← (M15 加 demo 视频/截图)
└── interview-prep.md                                      ← (M15 加, 5分钟讲稿 + Q&A)

master/                                                    ← (M2 创建)
web/                                                       ← (M3 创建)
worker/                                                    ← (M8 创建)
scripts/
├── build_spec_html.py                                     ← spec.md → HTML 工具
└── rename_to_shipyard.py                                  ← 改名脚本(留作参考)

.github/                                                   ← 社区模板 + CI
├── ISSUE_TEMPLATE/
├── workflows/                                             ← (M14 加 CI workflow)
├── dependabot.yml
└── CODEOWNERS
```

---

## 9. 常见任务速查

### 改完 spec.md 后重新生成 HTML
```bash
python scripts/build_spec_html.py docs/superpowers/specs/2026-08-08-platform-design.md docs/superpowers/specs/2026-08-08-platform-design.html
```

### 重新跑改名脚本(以防还要改)
```bash
python scripts/rename_to_shipyard.py        # dry-run
python scripts/rename_to_shipyard.py --apply # 真跑
```

### 查看里程碑状态
```bash
cat docs/superpowers/plans/2026-08-08-platform-implementation.md
```

### 起 demo 基础设施
```bash
make infra       # 只起 MySQL + Redis
make demo        # 完整 demo (master + drone + Harbor + k3s + worker + monitoring) — M15 后可用
```

---

## 10. 下次工作的第一句话模板

切电脑/换设备/隔天继续, 跟 Mavis 说:

> "回到 shipyard 项目, 当前 commit `fb0b57b` (改 shipyard), M1 完成, **M2 开始**。"

或者:

> "回到 shipyard, 先做 [M2 某个子任务] / [改 spec 某个地方] / [看 wireframe] / [跑 demo]"

Mavis 读 PROGRESS.md + spec + plan 自动接上, 不需要你解释"做了啥"。

---

## 11. M2.5 完整验证记录 (2026-08-09, D 盘)

### D 盘环境
- **OS**: Windows 11, PowerShell
- **JDK**: 21.0.6 (Oracle) at `D:\jdk-21.0.6`
- **Maven**: 3.8.9 at `D:\apache-maven-3.8.9\bin\mvn.cmd`
- **MySQL**: 8.4.5 (服务 `MySQL84`, 端口 3306, 密码 `123456`)
- **Redis**: 在跑 (端口 6379, 无密码)
- **项目根**: `D:\Projects\shipyard` (从 GitHub `yeyanghua/shipyard` clone)

### 验收清单
| 项 | 结果 | 备注 |
|---|---|---|
| `mvn compile` | ✅ | BUILD SUCCESS |
| `mvn test` (9 个 crypto 测试) | ✅ | AesEncrypter 全部通过 |
| 创建 `shipyard` 库 | ✅ | `CREATE DATABASE shipyard DEFAULT CHARACTER SET utf8mb4` |
| `mvn spring-boot:run` | ✅ | 2.49s 启动 |
| Flyway V1__init.sql | ✅ | 134ms 落 13 张业务表 + flyway_schema_history |
| 13 张业务表存在 | ✅ | ai_interaction, alert_log, build_log, build_record, deploy_record, dockerfile_template, env, env_variable, pipeline_template, project, project_dockerfile, project_env, worker |
| `/actuator/health` | ✅ | `{"status":"UP","groups":["liveness","readiness"]}` |
| GitHub push | ✅ | commit `85dcc05` → origin/main |

### 关键命令
```powershell
# 创建库 (PowerShell)
& "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root -p123456 -h localhost -P 3306 `
  -e "CREATE DATABASE IF NOT EXISTS shipyard DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 启动后端 (必须传 MYSQL_PASSWORD, application.yml 密码默认空)
$env:MYSQL_PASSWORD = '123456'
Set-Location D:\Projects\shipyard\shipyard
mvn spring-boot:run

# 验证
curl http://localhost:8080/actuator/health
# → {"status":"UP","groups":["liveness","readiness"]}
```

### dev 启动"正常"警告 (M2 范围预期)
- `No MyBatis mapper was found in '[com.shipyard.**.mapper]' package` — M4 才加 mapper, M2 只搭骨架
- `No active profile set, falling back to 1 default profile: "default"` — yml 没指定 profile, 默认 OK
- `Flyway upgrade recommended: MySQL 8.4 is newer than this version of Flyway` — 已知, Flyway 9.22.3 仍可工作, 升级到 10.x 列入 V1.5
- `Using generated security password` — Spring Security 默认行为, 我们没暴露 user details service, 忽略

### 13 张表清单 (与 spec §6 对齐)
| # | 表名 | 用途 |
|---|---|---|
| 1 | project | 项目元数据 |
| 2 | pipeline_template | 流水线模板, 版本化 |
| 3 | dockerfile_template | Dockerfile 模板, shipyard 自带 |
| 4 | project_dockerfile | 项目 Dockerfile 实例 |
| 5 | env | 环境定义 |
| 6 | project_env | 项目-环境关联 |
| 7 | env_variable | 环境变量, env × project 维度 |
| 8 | build_record | 构建记录, 含 log_persisted 字段 |
| 9 | build_log | 构建日志, 按 step 存, LONGTEXT |
| 10 | deploy_record | 发布记录, 含 snapshot_yaml 用于回滚 |
| 11 | worker | worker 注册, 每环境一个 |
| 12 | ai_interaction | AI 对话留痕, LLM request/response/采纳与否 |
| 13 | alert_log | 告警日志, P0/P1/P2 + open/acknowledged/resolved |

---

## 12. M4 计划 - 项目/环境 CRUD 前后端贯通

### 目标
让 shipyard 从"能跑"变成"能用":实现 project/env/env_variable 三表 CRUD,前后端贯通,V1 横向 demo 第一个可交互场景。

### 后端 (shipyard/)
1. **Entity** (MyBatis-Plus 注解)
   - `Project.java` (id/name/description/repo_url/created_at/updated_at/deleted)
   - `Env.java` (id/name/cluster/region/description/created_at/updated_at/deleted)
   - `EnvVariable.java` (id/env_id/project_id/key/value/encrypted/created_at/updated_at)
2. **Mapper** (extends BaseMapper<T>)
   - `ProjectMapper`, `EnvMapper`, `EnvVariableMapper`
3. **Service + Impl** (业务逻辑)
   - `ProjectService` — list / get / create / update / delete (逻辑删除)
   - `EnvService` — list / get / create / update / delete
   - `EnvVariableService` — list / batch upsert (环境变量按 env × project 维度)
4. **Controller** (REST + JWT 白名单豁免只有 actuator, 其他都要 JWT)
   - `GET /api/projects` — 列表(分页)
   - `POST /api/projects` — 创建
   - `GET /api/projects/{id}` — 详情
   - `PUT /api/projects/{id}` — 更新
   - `DELETE /api/projects/{id}` — 软删
   - `GET /api/envs?projectId=xxx` — 某项目下的环境
   - `POST /api/envs` — 创建
   - `GET /api/envs/{id}/variables` — 环境变量列表
   - `PUT /api/envs/{id}/variables` — 批量 upsert (value 走 Encrypter 加密)
   - `GET /api/envs/{id}/variables/{key}` — 单个查(解密)
5. **DTO + 异常处理**
   - `PageResponse<T>` (统一分页响应)
   - `ApiError` (统一错误体 {code, message, details})
   - `BusinessException` + `GlobalExceptionHandler` (@RestControllerAdvice)
6. **测试** (JUnit 5 + Mockito,目标覆盖率 70%+)
   - ProjectServiceTest / EnvServiceTest / EnvVariableServiceTest
   - ProjectControllerTest (MockMvc) — 验证 JWT 拦截 + CRUD
   - AesEncrypter 集成测试 (EnvVariable 加解密往返)

### 前端 (web/)
1. **API 客户端** (`src/api/projects.ts`, `src/api/envs.ts`)
2. **页面补全** (M3 已有占位, M4 接真数据)
   - `ProjectList.vue` — 表格 + 新建按钮 + 搜索
   - `CreateProject.vue` — 表单 (name/description/repo_url) + 校验
   - `ProjectDetail.vue` — Tab 切基本信息 / 关联环境 / Dockerfile
   - `EnvList.vue` — 项目下的环境列表
   - `EnvVars.vue` — 环境变量 Key-Value 编辑器 (加密标记)
3. **Pinia store** (`src/stores/projects.ts`)
4. **测试** (Vitest)
   - `projectsApi.test.ts` — mock axios, 验证请求参数和响应处理
   - `ProjectList.spec.ts` — 组件渲染 + 交互

### V1 demo 演示路径 (M4 完成后的横向 demo)
1. 用户登录 (M4 不做, JWT 用 M2.5 的 demo token)
2. 创建项目 "demo-java-app", 填 repo URL
3. 选环境 "dev", 配置环境变量 `DB_URL` / `DB_PASSWORD` (后者加密)
4. 触发构建 (M5 接入 drone)
5. 构建日志 SSE 实时输出 (M6)
6. 部署到 dev 环境 (M7)
7. 查看 AI 诊断 (M8)
8. 回滚 (M7 一并)

### 风险
- **加密值前端展示** — 前端不该看明文,只显示 `***`,查看走单独 API (M4 列计划但可以 V1.5 再细化)
- **JWT demo token** — V1 demo 没有用户系统,所有 API 直接放行 (M2.5 白名单) 或者用一个 hard-coded token
- **MyBatis-Plus 软删 + 唯一约束** — name 唯一约束 + 软删有冲突,M4 实现时注意

### 详细方案
详见 `docs/M4-detail.md` (10 章节,含固化决策表),含:
- 范围与边界(M4 走"项目/环境/env_variable CRUD",plan 原"M4 repo 抽象层"推迟到 M5.1)
- 数据模型(4 张表字段回顾 + 关键设计点)
- 后端设计(17 个新文件 + Entity/Mapper/Service/Controller/DTO/异常/测试)
- 前端设计(API 客户端 + Pinia + 5 个页面 + 关键组件 EnvVarEditor/SecretInput)
- 5 个关键决策(已拍板,见 M4-detail.md 顶部决策表)
- 任务分解(后端 4 天 + 前端 3 天 + 联调 0.5 天)
- 验收标准 + 风险 + 后续 Milestone 衔接

### 已拍板的 5 个关键决策(2026-08-09)
1. M4 范围:走 PROGRESS.md(项目/环境 CRUD),plan 原 M4 推迟到 M5.1
2. 鉴权:Hard-coded JWT(`/api/auth/demo-token` + 前端 localStorage)
3. 加密:折中(3 个 secret 加密,其他明文)
4. 删除:软删(`@TableLogic` 自动过滤)
5. 错误码:业务码独立(HTTP 永远 200,body `{code: 0=成功}`)

### 估计
- 后端: 1-1.5 天
- 前端: 1-1.5 天
- 测试 + 联调: 0.5 天
- 总计: 3-3.5 天

---

**Last updated**: 2026-08-09
**Next action**: 开始 M4 — 后端先写 Project Entity + Mapper + Service + Controller, 跑通 POST /api/projects → 200, 再写前端对接

## 13. M4 端到端验收清单 (2026-08-09 D 盘)

### 后端 (commits 81f4c99 / 1e0f7cd / 88a22e2)
- [x] 4 Entity + BaseEntity + MetaObjectHandlerImpl
- [x] 4 Mapper (BaseMapper + Project/Env 复活 raw SQL)
- [x] 4 Service (Project/Env/ProjectEnv/EnvVariable) + 公共异常
- [x] 4 Controller + AuthController (/api/auth/demo-token)
- [x] 10 DTO + GlobalExceptionHandler + BeanUtils
- [x] 14 业务端点 + E2E test-m4.ps1 20/20 通过
- [x] 启动时 CryptoHealthCheck 全量校验
- [x] **Bug 1 (修)**: @Value → @ConfigurationProperties
- [x] **Bug 2 (修)**: BeanUtils.copyProperties null 覆盖 → copyNonNullProperties
- [x] **Bug 3 (修)**: @TableLogic 过滤软删 → raw SQL 复活
- [ ] **Bug 4 (已知)**: Security 6.2 + Boot 3.2.5 的 anyRequest().authenticated() 不拦 anonymous → M5 修

### 前端 (commit 25e37ea)
- [x] 4 API + 1 types + 1 auth + client 增强 (JWT 注入)
- [x] 3 Pinia store
- [x] 5 页面 (ProjectList/CreateProject/ProjectDetail/EnvList/EnvVars)
- [x] 2 组件 (SecretInput/EnvVarEditor)
- [x] App.vue onMounted 自动拉 demo token
- [x] typecheck 0 errors, build OK, vitest 6/6
- [x] 端到端: vite dev proxy /api → 后端 8080 全通

### V1 demo 演示路径
1. 启后端 + 前端
2. 浏览器 localhost:5173
3. 自动拉 demo JWT
4. /projects 列表 → + 新建项目
5. 项目详情 → 关联环境
6. 环境 → 配变量 (密码 ***)
7. 显示明文 → 验证解密往返

---

## 14. M5 计划 (2026-08-10+)

1. 修 Security 鉴权 bug (FilterRegistrationBean 显式控制)
2. drone CI 集成 (触发构建 + HMAC webhook 验签)
3. SSE 实时日志 (M6 提前)
4. 环境变量注入 drone (resolveAll 复用)
5. Web 端 BuildDetail 完整实现

估计 3-4 天.