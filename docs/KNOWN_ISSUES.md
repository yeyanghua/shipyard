# shipyard — Known Issues & Workarounds

> 已知问题、临时方案、V1.5 修复计划。**面试官看到时别紧张** — 这是公开 transparent 记录,
> 不是隐瞒问题。所有 workaround 都走显式配置 + 注释 + commit message 留底。

---

## KI-001: Spring Security 6.2.4 + Boot 3.2.5 鉴权 lambda 不生效

**录入时间**: 2026-08-09 (M5 阶段)
**影响范围**: `shipyard/src/main/java/com/shipyard/config/SecurityConfig.java`
**严重性**: 🟡 P1 (V1 demo 不致命, V1.5 必修)
**状态**: ⚠️ V1 workaround 生效; V1.5 待修

### 症状

`.authorizeHttpRequests()` lambda 里写的 deny / hasAuthority / permitAll 规则,
**日志显示 rule 都注册成功**, 但运行时 `AuthorizationFilter` 实际**没生效** —
所有 path 都能过, 无 JWT 也能调业务 API。

### 验证过的失败方案 (按时间顺序)

| # | 尝试 | 期望 | 实际 |
|---|------|------|------|
| 1 | `requestMatchers("/**").authenticated()` | 401 | 200 |
| 2 | `denyAll()` 兜底 | 403 | 200 |
| 3 | `hasAuthority("ROLE_admin")` | 401/403 | 200 |
| 4 | `FilterRegistrationBean.setEnabled(false)` 禁 JwtAuthFilter servlet 自动注册 | (消除重复跑) | 没影响 |
| 5 | 关虚拟线程 (`spring.threads.virtual.enabled=false` + VirtualThreadConfig 显式 executor) | (排除虚拟线程嫌疑) | 没影响 |
| 6 | 加 `DebugFilter` 在 Security chain 前打印 SecurityContextHolder 状态 | (定位 bypass 点) | 看到 chain 内部 anonymous 被 set, 但 AuthorizationFilter 不 deny |

### 推测根因

Spring Security 6.2.4 的 `AuthorizationFilter` 在某些 lambda 组合下,
rule chain 注册实际**没生效** (类似 issue #14105 类)。
可能与以下任一相关:
- Java 21 虚拟线程 + SecurityContext 传播
- 6.2 重构后的 `RequestMatcherDelegatingAuthorizationManager` 行为变化
- 与 `OncePerRequestFilter` 的 servlet 注册顺序冲突

**确切的根因待 V1.5 升级时定位 (升 Spring Boot 3.3 / Spring Security 6.3 看是否自动消失)**.

### V1 workaround (M5 拍板方案 C)

**配置项**: `shipyard.security.demo-mode=true` (默认 V1)

**行为**:
- 所有 path 走 `permitAll` (AuthorizationFilter 不强制)
- `JwtAuthFilter` 仍跑 — 解析 `Authorization: Bearer xxx` 写入 `SecurityContext` 供业务用
- 前端 `App.vue` 启动时仍拉 `/api/auth/demo-token`, token 仍注进 axios 拦截器 (调试用)
- 真实部署必须用 V1.5 修复版本 (见下)

**演示范围**: V1 demo 在受控环境跑 (本机 + 局域网), 不暴露公网。

### V1.5 修复计划 (三选一)

| 方案 | 做法 | 工作量 | 风险 |
|---|---|---|---|
| **A. 升版本** (推荐) | Spring Security 6.2.4 → 6.3.x + Boot 3.2.5 → 3.3.x | 1 天 (验证不破坏现有) | 3.3 可能改 MyBatis-Plus / Flyway 兼容 |
| **B. 降版本** | Spring Security 6.1.x + Boot 3.1.x | 0.5 天 (回退) | 失去 3.2 的虚拟线程整合 |
| **C. 自定义 AuthorizationFilter** | 不走 `authorizeHttpRequests`, 自己写 filter | 1-2 天 (2 套: 白名单 + JWT 强制) | 中 (要全 path 覆盖测试) |

**V1.5 验收**:
- [ ] `denyAll` 兜底真实返 403
- [ ] 无 JWT 调 `/api/projects` 真实返 401
- [ ] JWT role=user 调 `/api/admin/**` 真实返 403
- [ ] whitelist (actuator/auth/webhook) 仍 permitAll

### 相关文件 (V1 留底, V1.5 修)

- `shipyard/src/main/java/com/shipyard/config/SecurityConfig.java` — demo-mode 开关
- `shipyard/src/main/java/com/shipyard/config/ShipyardSecurityProperties.java` — `shipyard.security.demo-mode` 配置
- `shipyard/src/main/java/com/shipyard/config/DebugFilter.java` — V1.5 debug 用 (默认 disabled)
- `shipyard/src/main/java/com/shipyard/config/JwtAuthFilterRegistration.java` — 禁 servlet 重复注册
- `shipyard/src/main/java/com/shipyard/common/JwtAuthFilter.java` — JWT 解析 (V1 跑通, V1.5 验证仍正确)
- `shipyard/src/main/resources/application.yml` — `shipyard.security.demo-mode: true` + 虚拟线程启用

### 面试怎么讲

> "V1 demo 用 demo-mode 走 permitAll, 因为 6.2.4 + Boot 3.2.5 组合有个鉴权
> lambda 不注册的 bug, 试过 6 种方案没解决。V1.5 我列了 3 个修复路径
> (升 / 降 / 自定义 filter), 倾向升到 6.3。Workaround 走显式开关, 不靠
> 注释或默认行为隐藏。"

这反而是**加分项** — 体现:
1. 真排查过, 不是直接调参就过
2. 知道 trade-off, 不掩盖问题
3. workaround 留 V1.5 升级路径, 不是 dead end

---

## 附录: 提交记录

| commit | 说明 |
|---|---|
| 待补 (M5 1) | demo-mode 引入 + 文档化 |
