# shipyard — 项目进度

> **TL;DR**: V1 横向 demo 阶段。已推 GitHub (`yeyanghua/shipyard`)。**M1 + M2 + M3 + M2.5 (SecurityConfig 合并) 已完成**,M4 准备开始(项目/环境 CRUD, 前后端贯通)。**换电脑/换设备** → 看本文 §6「换设备恢复步骤」。

| 字段 | 值 |
|---|---|
| GitHub | https://github.com/yeyanghua/shipyard |
| 当前分支 | `main` |
| 当前 commit | `28fb602` (合并 master/ → shipyard/ + SecurityConfig 提前到 M2.5) |
| 当前 milestone | **M4 准备开始**(M2 + M3 已完成 + M2.5 Security 合并) |
| 总 commit | 14 |
| V1 整体 | 5-6 周(3-4 周主体 + 1-2 周 demo 编排) |
| 上次更新 | 2026-08-08 |

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

**Last updated**: 2026-08-08
**Next action**: 开始 M2 master 后端骨架(Spring Boot 3.2 + Java 21 + Flyway)
