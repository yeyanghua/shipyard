# M4 详细方案 — 项目/环境/env_variable CRUD 前后端贯通

> **作者**: Mavis (Mavis Code) · **日期**: 2026-08-09 · **状态**: ✅ user 拍板 (2026-08-09)

## 决策固化

| 决策点 | 选择 | 影响范围 |
|---|---|---|
| **M4 范围** | 走 PROGRESS.md §12 (项目/环境 CRUD) | 不做 repo 抽象层,推迟到 M5.1 (与 M5 drone 集成合并) |
| **鉴权策略** | B Hard-coded JWT | 后端新增 `/api/auth/demo-token` 端点;前端启动时拉 token 存 localStorage;所有 `/api/**` 走 JWT(actuator 仍白名单) |
| **加密范围** | C 折中 | 3 个 secret 加密:`env_variable.value` / `project.repo_token` / `env.worker_token`;其他明文 |
| **删除策略** | A 软删 | MyBatis-Plus `@TableLogic` 自动过滤;唯一约束冲突时 service 层"复活+更新" |
| **错误码体系** | B 业务码独立 | HTTP 永远 200,body `{code: 0=成功,非 0=业务码}`;前端 axios 拦截器一刀切 |

---

## 0. TL;DR

让 shipyard 从"能跑"变成"能用"——实现 project / env / env_variable 三张核心表 + project_env 关联表的完整 CRUD,前后端贯通。完成后用户可以在 Web 端创建项目、配置环境、设置环境变量(密码走 AES-256 加密存储),为后续 M5-M7 打下 API 基础。

**范围决策**:原 plan 的"M4 repo 抽象层"推迟到 M5.1,M4 走本文档范围。

---

## 1. 范围与边界

### 在范围内
- 5 张表 CRUD: `project`, `env`, `project_env`, `env_variable`, 加上 1 张关联表
- 后端: Entity / Mapper / Service / Controller / DTO / 异常处理 / 单元测试 + MockMvc 集成测试
- 前端: 5 个页面对接真数据 + Pinia store + API 客户端 + Vitest 测试
- 环境变量的加密存储(走已有的 `Encrypter` 接口 + `AesEncrypter`)
- 软删(逻辑删除,MyBatis-Plus `@TableLogic`)
- 全局错误处理 + 统一响应格式
- API 鉴权(JWT,M2.5 已配白名单,这次所有 `/api/**` 都要 JWT,V1 demo 用 hard-coded token 绕过)

### 不在范围内
- 用户登录 / 注册 / RBAC (V1 demo 用 hard-coded JWT,留到 V1.5)
- repo 抽象层(plan 原 M4 内容,推迟到 M5.1)
- pipeline 模板 / Dockerfile 模板 / 构建 / 发布 (M5-M7)
- AI 能力 (M10)
- WebSocket 实时推送 (M6 才用 SSE)

---

## 2. 数据模型(已落库,M4 直接用)

V1__init.sql 已经定义了 4 张相关表(本文以 M4 视角重述):

### 2.1 `project` 表
```sql
id              BIGINT PK  (雪花 ID)
name            VARCHAR(64) UNIQUE    -- 英文,小写,数字,中划线
display_name    VARCHAR(128) NOT NULL
repo_provider   VARCHAR(16)  NOT NULL -- gitlab / gitee (M4 暂不验证)
repo_url        VARCHAR(512) NOT NULL
repo_token_enc  TEXT NULL             -- AES-256 加密,M4 创建/更新时加密
default_branch  VARCHAR(64)  DEFAULT 'main'
project_type    VARCHAR(32)  NOT NULL -- java_maven / java_gradle / node_pnpm / python_poetry / other
project_meta    JSON NULL             -- {java_version, main_class, jar_name, port...}
description     VARCHAR(512) NULL
created_at, updated_at, deleted
```

### 2.2 `env` 表
```sql
id                  BIGINT PK
name                VARCHAR(64) UNIQUE     -- dev / staging / prod
display_name        VARCHAR(128) NOT NULL
cluster_type        VARCHAR(16)  DEFAULT 'k8s'
k8s_namespace       VARCHAR(64)  NOT NULL
worker_url          VARCHAR(512) NOT NULL
worker_token_enc    TEXT NULL             -- AES-256 加密
is_production       TINYINT DEFAULT 0
created_at, updated_at, deleted
```

### 2.3 `project_env` 表(项目-环境关联)
```sql
project_id  BIGINT NOT NULL
env_id      BIGINT NOT NULL
created_at  DATETIME DEFAULT NOW
PRIMARY KEY (project_id, env_id)   -- 复合主键
```

### 2.4 `env_variable` 表
```sql
id            BIGINT PK
env_id        BIGINT NOT NULL         -- 必填
project_id    BIGINT NULL             -- NULL = 全局变量;非 NULL = 项目级变量
var_key       VARCHAR(128) NOT NULL
var_value_enc TEXT NOT NULL           -- AES-256 加密
is_secret     TINYINT DEFAULT 1       -- 1=敏感(UI 隐藏),0=明文
description   VARCHAR(512) NULL
updated_by    VARCHAR(64) NOT NULL    -- 当前 V1 demo 写 "demo-user"
updated_at, created_at, deleted
UNIQUE KEY (env_id, project_id, var_key)
```

**关键设计点**:`env_variable` 用 `(env_id, project_id, var_key)` 复合唯一约束 + `project_id` 可空,这样:
- `(env_id=1, project_id=NULL, var_key='JAVA_HOME')` = 全局变量
- `(env_id=1, project_id=5, var_key='DB_URL')` = 项目 5 在 env 1 的专用变量
- 同 env 同 key 查询时,MySQL 优先匹配项目级(走 `idx_env_variable_project` 索引)

---

## 3. 后端设计

### 3.1 目录结构(新增 17 个文件)
```
shipyard/src/main/java/com/shipyard/
├── entity/                          (新增 4 个)
│   ├── Project.java
│   ├── Env.java
│   ├── ProjectEnv.java
│   └── EnvVariable.java
├── mapper/                          (新增 4 个)
│   ├── ProjectMapper.java
│   ├── EnvMapper.java
│   ├── ProjectEnvMapper.java
│   └── EnvVariableMapper.java
├── service/                         (新增 4 个,Service 接口)
│   ├── ProjectService.java
│   ├── EnvService.java
│   ├── ProjectEnvService.java
│   └── EnvVariableService.java
├── service/impl/                    (新增 4 个,Service 实现)
│   ├── ProjectServiceImpl.java
│   ├── EnvServiceImpl.java
│   ├── ProjectEnvServiceImpl.java
│   └── EnvVariableServiceImpl.java
├── controller/                      (新增 4 个)
│   ├── ProjectController.java
│   ├── EnvController.java
│   ├── ProjectEnvController.java
│   └── EnvVariableController.java
├── dto/                             (新增 8-10 个)
│   ├── ProjectCreateRequest.java
│   ├── ProjectUpdateRequest.java
│   ├── ProjectResponse.java
│   ├── EnvCreateRequest.java
│   ├── EnvUpdateRequest.java
│   ├── EnvResponse.java
│   ├── EnvVariableUpsertRequest.java   -- 批量 upsert
│   ├── EnvVariableResponse.java        -- 列表用(隐藏 value)
│   ├── EnvVariableDecryptedResponse.java  -- 单个查(明文)
│   └── PageResponse.java               -- 统一分页
└── common/exception/                (新增 3 个)
    ├── BusinessException.java
    ├── ErrorCode.java             -- 枚举: 400/401/403/404/409/500 + 业务码
    └── GlobalExceptionHandler.java  -- @RestControllerAdvice
```

```
shipyard/src/test/java/com/shipyard/
├── service/                         (新增 4 个测试类)
│   ├── ProjectServiceTest.java
│   ├── EnvServiceTest.java
│   ├── ProjectEnvServiceTest.java
│   └── EnvVariableServiceTest.java
├── controller/                      (新增 4 个 @WebMvcTest)
│   ├── ProjectControllerTest.java
│   ├── EnvControllerTest.java
│   ├── ProjectEnvControllerTest.java
│   └── EnvVariableControllerTest.java
└── crypto/
    └── AesEncrypterTest.java        (已有,M4 复用)
```

### 3.2 Entity 设计要点

所有 Entity 用 **MyBatis-Plus 注解** + **Lombok**:
- 主键:`@TableId(type = IdType.ASSIGN_ID)` (雪花)
- 软删:`@TableLogic` (自动过滤 deleted=0)
- 自动时间:`@TableField(fill = FieldFill.INSERT)` + `MetaObjectHandler`
- JSON 字段:`@TableField(typeHandler = JacksonTypeHandler.class)` (project_meta, variable_values)

```java
@Data
@TableName("project")
public class Project {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String displayName;
    private String repoProvider;
    private String repoUrl;
    private String repoTokenEnc;     // 加密字段
    private String defaultBranch;
    private String projectType;
    private String projectMeta;      // JSON 字符串
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
```

### 3.3 Service 层核心逻辑

#### 3.3.1 ProjectService
```java
public interface ProjectService {
    Page<Project> list(int page, int size, String keyword);
    Project get(Long id);
    Project create(ProjectCreateRequest req);   // 校验 name 唯一
    Project update(Long id, ProjectUpdateRequest req);
    void delete(Long id);                         // 软删
}
```

#### 3.3.2 EnvVariableService(核心 + 最复杂)
```java
public interface EnvVariableService {
    /** 批量 upsert: 同 (env, project, key) 更新,否则新增 */
    List<EnvVariable> batchUpsert(Long envId, Long projectId, List<EnvVariableUpsertRequest> items, String updatedBy);

    /** 列表:返回时 is_secret=true 的 value 改成 "***" */
    List<EnvVariableResponse> list(Long envId, Long projectId);

    /** 单个查(解密,供构建时使用) */
    Map<String, String> resolveAll(Long envId, Long projectId);

    /** 启动时全量校验:所有加密字段都能解密(防"运行到一半 500") */
    int validateAllOnStartup();
}
```

**`resolveAll()` 设计**(M7 会复用):
- 输入 env_id + project_id (project_id 可为 null)
- 查出该 env 下所有变量(全局 + 项目级,项目级优先)
- 解密 value
- 返回 `Map<String, String>`(可直接给 drone vars.yaml 用)

**优先级**:`(env, project, key)` > `(env, null, key)`(同 key 项目级覆盖全局)

#### 3.3.3 启动时加密完整性校验
```java
@Component
public class CryptoHealthCheck implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        int failed = envVariableService.validateAllOnStartup();
        if (failed > 0) {
            log.error("发现 {} 个 env_variable 解密失败,shipyard 启动中止", failed);
            throw new CryptoException("启动中止: " + failed + " 个加密变量损坏");
        }
        log.info("✅ 所有 env_variable 解密校验通过");
    }
}
```

### 3.4 Controller API 设计(14 个端点)

#### 项目(ProjectController) — `/api/projects`
| Method | Path | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/projects` | 列表(分页 + 关键字搜索) | JWT |
| GET | `/api/projects/{id}` | 详情 | JWT |
| POST | `/api/projects` | 创建 | JWT |
| PUT | `/api/projects/{id}` | 更新 | JWT |
| DELETE | `/api/projects/{id}` | 软删 | JWT |

#### 环境(EnvController) — `/api/envs`
| Method | Path | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/envs` | 列表(分页 + isProduction 过滤) | JWT |
| GET | `/api/envs/{id}` | 详情 | JWT |
| POST | `/api/envs` | 创建 | JWT |
| PUT | `/api/envs/{id}` | 更新 | JWT |
| DELETE | `/api/envs/{id}` | 软删 | JWT |

#### 项目-环境关联(ProjectEnvController) — `/api/projects/{projectId}/envs`
| Method | Path | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/projects/{projectId}/envs` | 项目关联的所有环境 | JWT |
| POST | `/api/projects/{projectId}/envs` | 关联环境(body: `{envId}`) | JWT |
| DELETE | `/api/projects/{projectId}/envs/{envId}` | 取消关联 | JWT |

#### 环境变量(EnvVariableController) — `/api/envs/{envId}/variables`
| Method | Path | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/envs/{envId}/variables?projectId=xxx` | 列表(secret 显示 `***`) | JWT |
| PUT | `/api/envs/{envId}/variables?projectId=xxx` | 批量 upsert (body: `[{key, value, isSecret, description}]`) | JWT |
| GET | `/api/envs/{envId}/variables/{key}?projectId=xxx` | 单个查(明文,带"显示明文"权限提示) | JWT |
| DELETE | `/api/envs/{envId}/variables/{key}?projectId=xxx` | 删除 | JWT |

### 3.5 DTO 设计要点

#### ProjectCreateRequest(校验)
```java
@Data
public class ProjectCreateRequest {
    @NotBlank @Pattern(regexp = "^[a-z0-9-]+$", max = 64)
    private String name;
    @NotBlank @Size(max = 128)
    private String displayName;
    @NotBlank @Pattern(regexp = "^(gitlab|gitee)$")
    private String repoProvider;
    @NotBlank @Size(max = 512)
    private String repoUrl;
    @Size(max = 512)  // 可选,填了则加密
    private String repoToken;
    @NotBlank @Pattern(regexp = "^(java_maven|java_gradle|node_pnpm|python_poetry|other)$")
    private String projectType;
    private String projectMeta;   // JSON 字符串,后端 parse 校验
    @Size(max = 512)
    private String description;
}
```

**Service 转换**:
```java
public Project create(ProjectCreateRequest req) {
    // 1. 校验 name 唯一
    if (projectMapper.selectCount(new LambdaQueryWrapper<Project>().eq(Project::getName, req.getName())) > 0) {
        throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "项目名已存在: " + req.getName());
    }
    // 2. DTO -> Entity
    Project p = new Project();
    BeanUtils.copyProperties(req, p, "repoToken");  // 跳过明文 token
    // 3. 加密 token
    if (StringUtils.hasText(req.getRepoToken())) {
        p.setRepoTokenEnc(encrypter.encrypt(req.getRepoToken()));
    }
    // 4. 写入
    projectMapper.insert(p);
    return p;
}
```

### 3.6 统一响应 + 全局异常

#### 统一响应
```java
@Data
public class ApiResponse<T> {
    private int code;          // 0=成功,非 0=失败
    private String message;    // 成功/失败描述
    private T data;            // 业务数据
    private long timestamp;    // 毫秒

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(0);
        r.setMessage("OK");
        r.setData(data);
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }
}
```

#### ErrorCode 枚举
```java
public enum ErrorCode {
    SUCCESS(0, "OK"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    RESOURCE_CONFLICT(409, "资源冲突"),       // name 重复等
    CRYPTO_ERROR(500, "加密失败"),
    INTERNAL_ERROR(500, "服务器内部错误");
    // ...
}
```

#### GlobalExceptionHandler
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ApiResponse.error(400, msg);
    }
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleGeneric(Exception e) {
        log.error("服务器异常", e);
        return ApiResponse.error(500, "服务器内部错误");
    }
}
```

### 3.7 测试策略

#### 单元测试(JUnit 5 + Mockito)
- 4 个 `*ServiceTest`:mock Mapper 和 Encrypter,验证业务逻辑
  - `ProjectServiceTest.create()`:name 重复时抛 `BusinessException`
  - `EnvVariableServiceTest.batchUpsert()`:同 key 覆盖,新 key 新增,value 加密
  - `EnvVariableServiceTest.resolveAll()`:项目级优先于全局
  - `EnvVariableServiceTest.validateAllOnStartup()`:解密失败抛 `CryptoException`

#### 切片测试(`@WebMvcTest`)
- 4 个 `*ControllerTest`:MockMvc + 加载 SecurityConfig
  - 无 JWT 头 → 401
  - 带 JWT 头 → 200
  - body 校验失败 → 400 + 错误信息
  - 业务异常 → 对应业务码

#### 不写集成测试
- M4 暂不写 Testcontainers 集成测试(避免启动慢 + 复杂度)
- 真实环境验证走手动 + M4 完成后 `mvn spring-boot:run` + curl/Postman 验证

---

## 4. 前端设计

### 4.1 目录结构(新增 + 修改 ~10 个文件)
```
web/src/
├── api/
│   ├── client.ts                  (已有,需扩展)
│   ├── projects.ts                (新增)
│   ├── envs.ts                    (新增)
│   └── envVariables.ts            (新增)
├── stores/
│   ├── projects.ts                (新增 Pinia)
│   └── envs.ts                    (新增 Pinia)
├── types/
│   ├── project.ts                 (新增)
│   ├── env.ts                     (新增)
│   └── envVariable.ts             (新增)
├── views/
│   ├── ProjectList.vue            (M3 占位 → M4 完整)
│   ├── CreateProject.vue          (M3 占位 → M4 完整)
│   ├── ProjectDetail.vue          (M3 占位 → M4 完整)
│   ├── EnvList.vue                (M3 占位 → M4 完整)
│   ├── EnvVars.vue                (M3 占位 → M4 完整)
│   └── __tests__/
│       ├── ProjectList.spec.ts    (新增)
│       └── CreateProject.spec.ts  (新增)
└── components/
    ├── EnvVarEditor.vue           (新增,Key-Value 编辑器)
    └── SecretInput.vue            (新增,密文输入框)
```

### 4.2 API 客户端(`api/projects.ts`)
```typescript
import { client } from './client';
import type { Project, ProjectCreateRequest, PageResponse } from '@/types';

export const projectsApi = {
  list: (params: { page: number; size: number; keyword?: string }) =>
    client.get<PageResponse<Project>>('/projects', { params }),

  get: (id: number) =>
    client.get<Project>(`/projects/${id}`),

  create: (req: ProjectCreateRequest) =>
    client.post<Project>('/projects', req),

  update: (id: number, req: ProjectUpdateRequest) =>
    client.put<Project>(`/projects/${id}`, req),

  delete: (id: number) =>
    client.delete(`/projects/${id}`),
};
```

### 4.3 关键 UI 组件

#### 4.3.1 `ProjectList.vue`(列表 + 搜索 + 新建)
- 顶部:搜索框 + "新建项目" 按钮
- 主体:表格(Name / DisplayName / Type / UpdatedAt / 操作)
- 操作:查看 / 编辑 / 删除(带确认)
- 分页:Element Plus 的 `el-pagination` 或自己写
- 空状态:引导用户创建第一个项目

#### 4.3.2 `CreateProject.vue`(表单 + 校验)
- 字段:Name(英文)/ DisplayName / RepoProvider(GitLab/Gitee) / RepoUrl / RepoToken(选填,密文) / ProjectType(下拉) / ProjectMeta(动态表单,按 type 切) / Description
- 校验:必填 / 格式 / 长度(用 VeeValidate 或自写)
- 提交:loading + 成功后跳 ProjectDetail

#### 4.3.3 `EnvVarEditor.vue`(核心 + 复杂)
- Key-Value 列表
- 每一行:Key(输入) / Value(明文,带 "设为密文" 开关) / Description(选填) / 删除按钮
- "新增变量"按钮
- 提交:批量 upsert(PUT)

#### 4.3.4 `SecretInput.vue`(密码框)
- 默认显示 `***`
- 点击"显示明文"按钮 → 后端单独查解密后的值
- 复制按钮(前端用 Clipboard API)

### 4.4 Pinia store 示例(`stores/projects.ts`)
```typescript
export const useProjectsStore = defineStore('projects', () => {
  const list = ref<Project[]>([]);
  const loading = ref(false);
  const pagination = reactive({ page: 1, size: 20, total: 0 });

  async function fetchList(keyword = '') {
    loading.value = true;
    try {
      const res = await projectsApi.list({ page: pagination.page, size: pagination.size, keyword });
      list.value = res.records;
      pagination.total = res.total;
    } finally {
      loading.value = false;
    }
  }

  async function createProject(req: ProjectCreateRequest) {
    const project = await projectsApi.create(req);
    await fetchList();   // 刷新
    return project;
  }

  return { list, loading, pagination, fetchList, createProject };
});
```

### 4.5 测试(Vitest)

#### `ProjectList.spec.ts`(组件测试)
```typescript
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ProjectList from '@/views/ProjectList.vue';
import { projectsApi } from '@/api/projects';

vi.mock('@/api/projects');

describe('ProjectList', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('渲染列表', async () => {
    vi.mocked(projectsApi.list).mockResolvedValue({
      records: [{ id: 1, name: 'demo', ... } as Project],
      total: 1, page: 1, size: 20,
    });
    const wrapper = mount(ProjectList);
    await flushPromises();
    expect(wrapper.text()).toContain('demo');
  });
});
```

#### `api/projects.test.ts`(已存在,扩展示例)
- 验证 list 调用时带的参数
- 验证响应归一化

---

## 5. 关键决策点(请你拍板)

### 决策 1: 加密字段范围 ❓

`A. 最小集` — 只加密 `env_variable.var_value_enc` + `project.repo_token_enc` + `env.worker_token_enc`(明文都不存,直接加密入库)

`B. 全加密` — 包括 `display_name` / `description` 之外的所有 text 字段

`C. 折中` — 仅加密 secret 类(repo_token, worker_token, env_variable value),其他明文

**我推荐 C** — 跟 spec §6 表格注释一致(只 secret 类加密),且前端能看到 displayName/description 方便排查。

### 决策 2: 软删 vs 硬删 ❓

`A. 软删` — `deleted` 字段(已有),MyBatis-Plus `@TableLogic` 自动过滤,查询 API 看不到已删数据

`B. 硬删` — `DELETE FROM` 真删

**我推荐 A** — V1__init.sql 已定义 `deleted` 字段,审计/恢复友好,且 MyBatis-Plus 已配 `logic-delete-field: deleted`。

### 决策 3: API 鉴权(V1 demo) ❓

`A. 全放行` — `/api/**` 加进白名单,跟 actuator 一样无需 JWT(最简单,V1 demo 跑得通)

`B. Hard-coded JWT` — 后端写死一个 token `demo-token-xxx`,前端所有请求都带这个,V1 demo 看起来"有认证"但实际是 mock

`C. 完整 JWT` — 写 `/api/auth/login` 接口 + 用户表(3 张表),V1 跑得动但工作量 +1 天

**我推荐 B** — 视觉上跟生产一致,白名单只放 actuator;V1 演示不会被"无鉴权"暴露,V1.5 再做完整用户系统。**B 需要做的:加 `/api/auth/demo-token` 端点返回固定 token + 前端启动时调一次存 localStorage**。

### 决策 4: 分页策略 ❓

`A. MyBatis-Plus 内置` — `Page<T>`,前端传 `page/size`,返回 `records/total/page/size`

`B. Cursor-based` — 适合大数据量,V1 用不到

**我推荐 A** — M2 已配 `PaginationInnerInterceptor`(看 `DataSourceConfig.java`),直接用。

### 决策 5: 错误码体系 ❓

`A. HTTP 状态码优先` — 404/400/500,业务码放 body

`B. 业务码独立` — body 里 `code` 字段(0=成功,非 0=业务码),HTTP 永远 200,前端用 `code` 判断

**我推荐 B** — 前端 axios 拦截器可以一刀切"业务码 != 0 就弹错",不依赖 HTTP 状态。

---

## 6. 任务分解与工时估计

| 阶段 | 任务 | 工时 | 依赖 |
|---|---|---|---|
| 后端 1 | 4 个 Entity + 4 个 Mapper + MyBatis-Plus 配置 | 0.5 天 | — |
| 后端 2 | ProjectService + EnvService + ProjectEnvService | 1 天 | 后端 1 |
| 后端 3 | EnvVariableService(加密/解密/resolve/validate) | 0.5 天 | 后端 1 + crypto 已有 |
| 后端 4 | 4 个 Controller + DTO + GlobalExceptionHandler | 1 天 | 后端 2-3 |
| 后端 5 | 单元测试 + Controller 测试 | 0.5 天 | 后端 2-4 |
| 后端 6 | 手动 curl 验证 14 个端点 | 0.5 天 | 后端 5 |
| 前端 1 | TypeScript 类型 + API 客户端 + Pinia store | 0.5 天 | — |
| 前端 2 | ProjectList + CreateProject + ProjectDetail | 1 天 | 前端 1 |
| 前端 3 | EnvList + EnvVars + EnvVarEditor + SecretInput | 1 天 | 前端 1 + 后端 4 |
| 前端 4 | Vitest 测试 | 0.5 天 | 前端 2-3 |
| 联调 | 端到端:Web 创项目 → 加 env → 配变量 → 查解密 | 0.5 天 | 全部 |
| **总计** | | **5-6 天** | |

---

## 7. 验收标准

### 后端
- [ ] `mvn compile` 通过
- [ ] `mvn test` 全部通过(新加 ~12 个测试 + 已有 9 个加密测试)
- [ ] `mvn spring-boot:run` 启动成功
- [ ] 启动日志:`✅ 所有 env_variable 解密校验通过` (空库也通过)
- [ ] curl 验证 14 个 API 端点都正常返回
- [ ] 无 JWT 头 → 401
- [ ] name 重复创建 → 409
- [ ] 加密字段入库后用 SQL 看到的是密文
- [ ] 解密接口返回明文跟加密前一致

### 前端
- [ ] `pnpm build` 成功
- [ ] `pnpm test` 全部通过
- [ ] `pnpm dev` 启动后能调通 14 个 API
- [ ] 创项目流程:填表 → 校验 → 提交 → 跳详情
- [ ] 配环境变量流程:加 Key-Value → 提交 → 列表显示 `***`
- [ ] 显示明文按钮:点击 → 后端查解密 → 显示

### 端到端(Demo 视频)
- [ ] 录一段 2-3 分钟视频:创建项目 → 加环境 → 配变量(密码密文) → 列表看 → 详情看加密
- [ ] 配合 M2.5 的 `{"status":"UP"}` 一起展示

---

## 8. 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| 加密密钥轮换时老数据不可用 | 中 | 高 | V1 用单密钥,envelope encryption 设计(每条记录独立 IV),V1.5 加 KMS 升级路径 |
| env_variable 启动校验耗时 | 低 | 中 | 启动只校验,不放请求路径;失败立即停止启动 |
| 软删 + 唯一约束冲突(同 name 删了又建) | 中 | 中 | service 层 create 前查询包含 `deleted=1` 的,直接 UPDATE 复活 + 更新其他字段 |
| 前端 Pinia store 类型推导不灵 | 低 | 低 | 严格 TS + `defineStore` setup 语法,IDE 实时检查 |
| Vue 3 + Element Plus 体积大 | 低 | 低 | 已有 vite vendor chunk,Element Plus 按需引入 |

---

## 9. 后续 Milestone 衔接

M4 完成后,以下 API 已有,可直接接后续:
- **M5 (drone 集成)**: `POST /api/projects/{id}/builds` 调 drone,需要 `ProjectService.get()` + `EnvVariableService.resolveAll()`
- **M6 (pipeline 模板)**: `ProjectController` + `PipelineController` 配合
- **M7 (env_variable 注入)**: `EnvVariableService.resolveAll()` 直接复用,`VariableInjector` 只负责拼 vars.yaml + 调 drone API
- **M12 (Dockerfile 模板)**: `ProjectController` 创建项目后引导到 Dockerfile 模板选择

---

**Last updated**: 2026-08-09
**Status**: 待 user 拍板 5 个关键决策 → 调整后开干
