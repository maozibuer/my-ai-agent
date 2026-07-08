# 系统设计文档

## 1. 架构概览

### 1.1 整体架构

智能问答Agent系统采用前后端分离架构，后端基于 Spring Boot 微服务设计，集成 LangChain4j 实现大语言模型（LLM）能力与检索增强生成（RAG）流程。

```
┌─────────────────────────────────────────────────────────────────────┐
│                        客户端层 (Client Layer)                       │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │   Web 浏览器      │  │   移动端浏览器    │  │   API 客户端      │  │
│  │  (Vue3 SPA)      │  │  (响应式适配)     │  │  (Postman/curl)  │  │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘  │
└───────────┼──────────────────────┼──────────────────────┼───────────┘
            │                      │                      │
            ▼                      ▼                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      网关层 (Gateway Layer)                          │
│                    Nginx 反向代理 + 静态资源服务                      │
│         - 前端静态资源服务 (Port 80)                                  │
│         - API 反向代理 (→ backend:8080)                              │
│         - SSE 流式转发 (proxy_buffering off)                         │
│         - Gzip 压缩                                                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    应用层 (Application Layer)                        │
│                  Spring Boot 3.2.5 (Port 8080)                      │
│                                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │Controller│→│ Service  │→│  Agent   │→│  Mapper  │ │ Security │  │
│  │  控制器   │ │  服务层   │ │ AI模块   │ │ 数据层    │ │ 安全模块  │  │
│  └──────────┘ └──────────┘ └────┬─────┘ └────┬─────┘ └──────────┘  │
│  ┌──────────┐                     │            │                     │
│  │  Config  │                     │            │                     │
│  │  配置层   │                     │            │                     │
│  └──────────┘                     │            │                     │
│  ┌──────────┐                     │            │                     │
│  │  Common  │                     │            │                     │
│  │  公共模块 │                     │            │                     │
│  └──────────┘                     │            │                     │
└───────────────────────────────────┼────────────┼─────────────────────┘
                                    │            │
              ┌─────────────────────┼────────────┼──────────┐
              │                     │            │          │
              ▼                     ▼            ▼          ▼
    ┌─────────────────┐  ┌──────────────┐  ┌────────┐  ┌────────┐
    │  LLM 服务        │  │Elasticsearch │  │ MySQL  │  │ Redis  │
    │  (OpenAI/通义等)  │  │  8.x         │  │  8.0   │  │  7.x   │
    │  大语言模型 API   │  │  向量+全文检索 │  │ 业务数据│  │ 缓存   │
    └─────────────────┘  └──────────────┘  └────────┘  └────────┘
```

### 1.2 技术选型理由

| 组件 | 选型 | 理由 |
|------|------|------|
| 后端框架 | Spring Boot 3.2.5 | 成熟的企业级框架，生态完善，支持 Java 21 虚拟线程 |
| AI 框架 | LangChain4j | Java 原生的 LLM 集成框架，支持 RAG、工具调用、流式输出 |
| 数据库 | MySQL 8.0 | 关系型数据库，支持事务、JSON 类型、全文索引 |
| 缓存 | Redis 7 | 高性能内存缓存，支持会话管理、限流、消息队列 |
| 搜索引擎 | Elasticsearch 8.x | 分布式搜索与分析引擎，支持向量检索和全文搜索 |
| 前端 | Vue 3 + Vite | 渐进式框架，组合式 API，构建速度快 |

---

## 2. 后端模块设计

后端为单一 Maven 模块，按职责划分为 8 个逻辑包：

### 2.1 controller - 控制器层

**职责：** 接收 HTTP 请求，参数校验，调用 Service 层处理业务逻辑，封装并返回统一响应。

**关键类：**

| 类名 | 说明 |
|------|------|
| AuthController | 认证接口：登录、注册、登出、获取用户信息 |
| ChatController | 对话接口：发送消息、流式对话、历史记录、会话管理 |
| KbController | 知识库接口：CRUD 操作 |
| DocumentController | 文档接口：上传、列表、状态查询、删除 |
| UserController | 用户管理接口：列表、更新（管理员） |
| LogController | 日志接口：操作日志查询（管理员） |

**设计规范：**
- 所有 Controller 使用 `@RestController` + `@RequestMapping("/api")`
- 使用 `@Validated` 进行参数校验
- 统一返回 `Result<T>` 响应对象
- 使用 `@Tag` (Swagger) 标注接口分组

### 2.2 service - 服务层

**职责：** 核心业务逻辑处理，协调各模块协作，事务管理。

**关键类：**

| 类名 | 说明 |
|------|------|
| AuthService | 用户认证、Token 生成与验证 |
| ChatService | 对话管理、消息处理、会话维护 |
| KbService | 知识库 CRUD、文档计数维护 |
| DocumentService | 文档上传、异步处理流程编排、状态管理 |
| UserService | 用户管理、权限校验 |
| LogService | 操作日志记录与查询 |

**设计规范：**
- 接口与实现分离：`XxxService` 接口 + `XxxServiceImpl` 实现
- 使用 `@Transactional` 管理事务
- 使用 `@Async` 实现异步文档处理

### 2.3 domain - 领域层

**职责：** 定义实体类、数据传输对象（DTO）、值对象。

**关键类：**

| 类型 | 类名 | 说明 |
|------|------|------|
| Entity | SysUser | 用户实体，映射 sys_user 表 |
| Entity | ChatRecord | 聊天记录实体，映射 chat_record 表 |
| Entity | KbBase | 知识库实体，映射 kb_base 表 |
| Entity | KbDocument | 文档实体，映射 kb_document 表 |
| Entity | SysLog | 操作日志实体，映射 sys_log 表 |
| DTO | LoginRequest | 登录请求 DTO |
| DTO | ChatRequest | 对话请求 DTO |
| DTO | KbCreateRequest | 知识库创建请求 DTO |
| DTO | PageResult | 分页结果通用 DTO |

**设计规范：**
- Entity 类使用 `@TableName` (MyBatis-Plus) 映射表
- 所有实体继承 `BaseEntity` 基类（含公共审计字段）
- DTO 使用 `@Valid` 校验注解

### 2.4 mapper - 数据访问层

**职责：** 数据库 CRUD 操作，基于 MyBatis-Plus。

**关键接口：**

| 接口名 | 说明 |
|------|------|
| SysUserMapper | 用户数据访问 |
| ChatRecordMapper | 聊天记录数据访问 |
| KbBaseMapper | 知识库数据访问 |
| KbDocumentMapper | 文档数据访问 |
| SysLogMapper | 操作日志数据访问 |

**设计规范：**
- 继承 `BaseMapper<T>` 获取基础 CRUD
- 复杂查询使用 XML 映射文件或 `@Select` 注解
- 逻辑删除通过 MyBatis-Plus `@TableLogic` 实现

### 2.5 config - 配置层

**职责：** 各组件的配置与初始化。

**关键类：**

| 类名 | 说明 |
|------|------|
| ElasticsearchConfig | Elasticsearch 客户端配置 |
| RedisConfig | Redis 连接池、序列化配置 |
| LangChain4jConfig | LLM 模型配置、Embedding 模型配置、RAG 流程配置 |
| WebMvcConfig | 跨域配置、拦截器注册 |
| MyBatisPlusConfig | 分页插件、乐观锁插件配置 |
| SwaggerConfig | OpenAPI 文档配置 |

### 2.6 common - 公共模块

**职责：** 全局通用组件：统一响应、异常处理、工具类、常量。

**关键类：**

| 类名 | 说明 |
|------|------|
| Result | 统一响应封装 `Result<T>` |
| ResultCode | 响应状态码枚举 |
| GlobalExceptionHandler | 全局异常处理器 (`@RestControllerAdvice`) |
| BusinessException | 业务异常 |
| PageResult | 分页结果封装 |
| Constants | 系统常量定义 |
| JwtUtils | JWT 工具类 |
| RedisUtils | Redis 操作工具类 |

### 2.7 security - 安全模块

**职责：** 认证、授权、请求拦截。

**关键类：**

| 类名 | 说明 |
|------|------|
| JwtAuthenticationFilter | JWT 认证过滤器 |
| SecurityConfig | Spring Security 配置（如使用）或自定义拦截器配置 |
| JwtTokenProvider | JWT Token 生成与验证 |
| PasswordEncoderConfig | BCrypt 密码编码器配置 |
| UserContext | 当前用户上下文（ThreadLocal） |

**安全流程：**
1. 用户登录 -> 验证密码（BCrypt） -> 生成 JWT Token
2. 后续请求 -> JwtAuthenticationFilter 拦截 -> 验证 Token -> 设置 UserContext
3. 接口权限 -> 检查 UserContext 中的角色 -> ADMIN/USER 权限校验

### 2.8 agent - AI Agent 模块

**职责：** 大语言模型集成、RAG 检索增强生成、知识库向量化。

**关键类：**

| 类名 | 说明 |
|------|------|
| ChatAgent | LangChain4j AI Service 接口定义（`@AiService`） |
| KbEmbeddingService | 文档向量化服务 |
| KbRetrievalService | 知识库检索服务（Elasticsearch 向量检索） |
| DocumentProcessor | 文档处理：文本提取、分块 |
| ContentRetriever | RAG 内容检索器，实现 `RetrievalAugmentor` |
| StreamResponseHandler | SSE 流式响应处理器 |

**RAG 流程：**
1. **文档入库**：上传文档 -> 提取文本 -> 分块（Chunking） -> 向量化（Embedding） -> 存入 Elasticsearch
2. **问答检索**：用户提问 -> 问题向量化 -> Elasticsearch 向量检索（KNN） -> 召回相关文档块
3. **增强生成**：拼接用户问题 + 检索到的上下文 -> 发送至 LLM -> 流式返回生成结果

---

## 3. 数据流设计

### 3.1 对话数据流

```
用户输入消息
    │
    ▼
┌─────────────┐
│ ChatController│ ── 接收请求，参数校验
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  ChatService │ ── 保存用户消息到 MySQL (chat_record)
└──────┬──────┘
       │
       ▼
┌──────────────────┐
│ KbRetrievalService│ ── 向量化用户问题
│                  │ ── Elasticsearch KNN 向量检索
│                  │ ── 召回 Top-K 相关文档块
└──────┬───────────┘
       │
       ▼
┌─────────────┐
│  ChatAgent   │ ── 拼接 Prompt（系统提示 + 上下文 + 用户问题）
│ (LangChain4j)│ ── 调用 LLM API
└──────┬──────┘
       │
       ▼  (SSE 流式)
┌─────────────────┐
│StreamResponseHandler│ ── 逐块接收 LLM 输出
│                  │ ── 通过 SSE 推送至前端
└──────┬───────────┘
       │
       ▼
┌─────────────┐
│  ChatService  │ ── 流结束后保存 AI 回复到 MySQL (chat_record)
└──────────────┘
```

### 3.2 文档处理数据流

```
用户上传文档
    │
    ▼
┌──────────────────┐
│DocumentController │ ── 接收 MultipartFile
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ DocumentService   │ ── 保存文件信息到 MySQL (kb_document, status=PENDING)
│                  │ ── 触发异步处理
└──────┬───────────┘
       │ @Async
       ▼
┌──────────────────┐
│DocumentProcessor  │ ── 状态更新: PROCESSING
│                  │ ── 文本提取 (Apache Tika / PDFBox)
│                  │ ── 文本分块 (TokenTextSplitter)
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│KbEmbeddingService │ ── 分块文本向量化 (Embedding Model)
│                  │ ── 写入 Elasticsearch (向量 + 原文)
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ DocumentService   │ ── 状态更新: COMPLETED
│                  │ ── 更新 chunk_count
│                  │ ── 更新 kb_base.document_count
└──────────────────┘
```

### 3.3 认证数据流

```
用户登录请求
    │
    ▼
┌─────────────┐
│AuthController│ ── 接收 username + password
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ AuthService  │ ── 查询 MySQL: SysUser (by username)
│             │ ── BCrypt 密码比对
│             │ ── 失败: 抛出 AuthenticationException
│             │ ── 成功: 生成 JWT Token
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Redis        │ ── 存储 Token -> UserId 映射（用于登出失效）
└─────────────┘
       │
       ▼
   返回 Token + UserInfo

────── 后续请求 ──────

每次请求
    │
    ▼
┌──────────────────────┐
│JwtAuthenticationFilter│ ── 提取 Authorization Header
│                      │ ── 验证 JWT 签名 + 过期时间
│                      │ ── 检查 Redis 中 Token 有效性
│                      │ ── 设置 UserContext (ThreadLocal)
└──────────────────────┘
```

---

## 4. 安全设计

### 4.1 认证机制

| 方面 | 方案 |
|------|------|
| 密码存储 | BCrypt 加密（代价因子 10），不可逆 |
| Token 方案 | JWT (HS256)，Header.Payload.Signature |
| Token 有效期 | Access Token: 24 小时 |
| Token 失效 | Redis 黑名单机制，登出时加入黑名单 |
| 传输安全 | HTTPS (生产环境)，HTTP 仅开发环境 |

### 4.2 授权机制

采用基于角色的访问控制（RBAC）：

| 角色 | 权限 |
|------|------|
| ADMIN | 全部接口：用户管理、知识库管理、文档管理、日志查看、对话 |
| USER | 对话、知识库查看、文档上传（本人知识库） |

权限校验通过 `@PreAuthorize` 注解或自定义拦截器实现：
- 管理员接口（用户管理、日志查看）标注 `@RequireRole("ADMIN")`
- 普通接口仅需认证（Token 有效即可）

### 4.3 多租户隔离

- 所有业务表包含 `tenant_id` 字段
- MyBatis-Plus 拦截器自动在 SQL 中拼接 `tenant_id` 条件
- 用户 Token 中携带 `tenant_id`，登录后自动注入查询条件
- 不同租户的数据在逻辑层面完全隔离

### 4.4 数据安全

| 方面 | 措施 |
|------|------|
| SQL 注入 | MyBatis 参数化查询，禁止 SQL 拼接 |
| XSS 防护 | 前端输出编码，后端输入过滤 |
| CSRF | JWT Token 认证（无 Cookie Session，天然防 CSRF） |
| 文件上传 | 类型白名单校验（PDF/DOCX/TXT/MD）、大小限制（100MB） |
| 接口限流 | Redis + 令牌桶算法，防恶意请求 |
| 敏感数据 | 密码、Token 不记录到日志；日志中参数脱敏 |

### 4.5 操作审计

- 所有写操作通过 AOP 自动记录到 `sys_log` 表
- 日志内容：用户ID、操作描述、方法名、请求参数、IP、耗时
- 管理员可通过日志模块查询和追溯操作历史
- 日志支持按用户、操作类型、时间范围筛选

---

## 5. 数据库设计

### 5.1 ER 关系

```
┌──────────────┐       ┌──────────────┐       ┌──────────────────┐
│   sys_user    │       │  chat_record │       │     sys_log      │
│──────────────│       │──────────────│       │──────────────────│
│ id (PK)      │◀──┐   │ id (PK)      │       │ id (PK)          │
│ username     │   │   │ user_id (FK)─│──┐    │ user_id (FK)     │──┐
│ password     │   └──│ session_id   │  │    │ operation        │  │
│ email        │       │ user_message │  │    │ method           │  │
│ role         │       │ assistant_msg│  │    │ params           │  │
│ tenant_id    │       │ tenant_id    │  │    │ ip               │  │
└──────────────┘       └──────────────┘  │    │ time             │  │
                                         │    │ tenant_id        │  │
┌──────────────┐       ┌──────────────┐  │    └──────────────────┘  │
│   kb_base    │       │  kb_document │  │              ▲           │
│──────────────│       │──────────────│  │              │           │
│ id (PK)      │◀─────│ kb_base_id(FK)│  └──────────────────────────┘
│ name         │   ┌──│ id (PK)      │
│ department   │   │  │ file_name    │
│ doc_count    │   │  │ status       │
│ tenant_id    │   │  │ tenant_id    │
└──────────────┘   │  └──────────────┘
                   └────────────────────── (kb_base.id = kb_document.knowledge_base_id)
```

### 5.2 索引策略

| 表 | 索引 | 类型 | 说明 |
|----|------|------|------|
| sys_user | uk_username | UNIQUE | 用户名唯一约束 |
| sys_user | idx_tenant_id | NORMAL | 租户隔离查询 |
| chat_record | idx_user_id | NORMAL | 按用户查询历史 |
| chat_record | idx_session_id | NORMAL | 按会话查询记录 |
| chat_record | idx_create_time | NORMAL | 时间范围查询 |
| kb_base | idx_name | NORMAL | 名称搜索 |
| kb_base | idx_department | NORMAL | 部门筛选 |
| kb_document | idx_knowledge_base_id | NORMAL | 按知识库查询文档 |
| kb_document | idx_status | NORMAL | 按状态筛选 |
| sys_log | idx_user_id | NORMAL | 按用户查询日志 |
| sys_log | idx_create_time | NORMAL | 时间范围查询 |
| sys_log | idx_operation | NORMAL | 按操作类型筛选 |

### 5.3 逻辑删除

所有表包含 `deleted` 字段（TINYINT，0=未删除，1=已删除）：
- MyBatis-Plus `@TableLogic` 自动处理
- 查询时自动拼接 `WHERE deleted = 0`
- 删除操作实际执行 `UPDATE SET deleted = 1`
- 保留数据可恢复，满足审计需求

---

## 6. 缓存设计

### 6.1 Redis 使用场景

| 场景 | Key 格式 | TTL | 说明 |
|------|----------|-----|------|
| Token 黑名单 | `auth:blacklist:{token}` | 24h | 登出后 Token 加入黑名单 |
| 用户信息缓存 | `user:info:{userId}` | 30m | 缓存用户信息，减少 DB 查询 |
| 会话上下文 | `chat:context:{sessionId}` | 2h | 缓存多轮对话上下文 |
| 接口限流 | `ratelimit:{userId}:{api}` | 1m | 令牌桶限流计数 |
| 文档处理锁 | `doc:lock:{docId}` | 30m | 防止文档重复处理 |

### 6.2 缓存策略

- **缓存穿透**：对不存在的 Key 缓存空值（TTL 5分钟）
- **缓存击穿**：使用互斥锁（Redis SETNX）防止热点 Key 过期时大量请求穿透
- **缓存雪崩**：TTL 添加随机偏移（±60s），避免同时过期

---

## 7. 部署设计

### 7.1 容器化架构

```
Docker Compose 编排：
├── agent-mysql          (MySQL 8.0, Port 3306)
├── agent-redis          (Redis 7-alpine, Port 6379)
├── agent-elasticsearch  (ES 8.13.0, Port 9200/9300)
├── agent-backend        (Spring Boot, Port 8080)
└── agent-frontend       (Nginx, Port 80)
```

- 所有服务通过 `agent-network` 桥接网络通信
- 服务间使用容器名作为主机名（如 `mysql:3306`）
- 数据持久化通过 Docker Volume（mysql-data, redis-data, es-data）
- 健康检查确保依赖服务就绪后再启动后端

### 7.2 环境配置

| 环境 | Profile | 说明 |
|------|---------|------|
| 开发 | dev | 本地开发，连接本地中间件 |
| 生产 | prod | Docker 部署，连接容器内中间件 |

关键环境变量：
- `SPRING_PROFILES_ACTIVE`: 激活的配置文件
- `SPRING_DATASOURCE_URL`: MySQL 连接地址
- `SPRING_REDIS_HOST`: Redis 主机地址
- `ELASTICSEARCH_HOST`: Elasticsearch 主机地址
