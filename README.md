# AI Agent System

企业级智能问答平台，基于大语言模型（LLM）和检索增强生成（RAG）技术，为团队提供"问什么答什么"的知识助手服务。

## 它能做什么

- 上传 PDF、Word、Markdown 等文档，自动解析并建立知识库
- 向知识库提问，AI 结合文档内容给出准确回答
- 支持流式输出，像 ChatGPT 一样逐字显示
- 多用户多团队隔离使用，互不干扰
- 完整的操作日志，方便审计追溯

## 怎么工作的

用户提问 → 系统从知识库检索相关内容 → 把"问题 + 检索到的资料"一起发给大模型 → 大模型生成回答 → 流式返回给用户。

简单说就是：**先搜后答，让 AI 看着资料说话，而不是凭空编造。**

## 技术选型

| 层级 | 技术 | 为什么选它 |
|------|------|-----------|
| 后端框架 | Spring Boot 3.2 | 生态成熟，开发效率高 |
| AI 框架 | LangChain4j 0.34 | Java 生态最好的 LLM 集成框架 |
| 语言 | Java 21 | 最新的 LTS 版本，虚拟线程等特性 |
| 数据库 | MySQL 8.0 | 稳定可靠，业务数据存储 |
| 缓存 | Redis 7 | 会话缓存、Token 黑名单、限流 |
| 搜索引擎 | Elasticsearch 8 | 全文检索 + 向量检索，支撑 RAG |
| 前端 | Vue 3 + Element Plus | 上手快，组件丰富 |
| 构建 | Vite 5 | 比 Webpack 快得多 |
| 部署 | Docker Compose | 一条命令启动全部服务 |

对接的大模型：阿里云百炼（GLM-5 对话模型 + text-embedding-v3 向量模型）。

## 项目结构

```
ai-agent-system/
├── backend/                 # 后端 Spring Boot 项目
│   └── src/main/java/com/agent/
│       ├── api/             # 控制器 + 全局异常处理
│       ├── auth/            # 认证授权（JWT + RBAC）
│       ├── agentcore/       # AI Agent 核心（对话、RAG、提示词）
│       ├── knowledge/       # 知识库（文档解析、向量化、混合搜索）
│       ├── memory/          # 记忆管理（短期/长期、热点缓存）
│       ├── tool/            # 工具调用（天气、统计、外部 API）
│       ├── common/          # 公共模块（统一响应、异常、DTO）
│       └── infrastructure/  # 基础设施配置（Redis、ES、线程池）
├── frontend/                # 前端 Vue3 项目
│   └── src/
│       ├── views/           # 页面（登录、对话、知识库、用户管理）
│       ├── api/             # Axios 封装 + 各模块 API
│       ├── stores/          # Pinia 状态管理
│       └── router/          # 路由配置
├── database/init.sql        # 数据库初始化脚本
├── docker/                  # Docker 部署配置
│   ├── docker-compose.yml   # 一键编排 MySQL + Redis + ES + 前后端
│   ├── Dockerfile-backend
│   ├── Dockerfile-frontend
│   └── nginx.conf
└── docs/                    # 额外文档（API 文档、设计文档）
```

## 环境要求

开始之前，确保你的电脑上装了这些：

| 工具 | 版本 | 干什么用 |
|------|------|---------|
| JDK | 21+ | 编译运行后端 |
| Maven | 3.9+ | 后端依赖管理 |
| Node.js | 18+ | 前端构建 |
| MySQL | 8.0 | 存业务数据 |
| Redis | 7.0 | 缓存和会话 |
| Elasticsearch | 8.0 | 知识库搜索 |
| Docker | 24+ | 一键部署（可选） |

## 快速开始

### 🐳 Docker 一键启动（推荐）

如果你装了 Docker，这是最快的方式：

```bash
cd docker
docker-compose up -d --build
```

等待几分钟，然后访问：

- 前端页面：http://localhost
- 后端 API：http://localhost:8080
- Swagger 文档：http://localhost:8080/swagger-ui.html

默认账号：`admin` / `admin123`

### 🔧 手动启动

#### 1. 准备基础设施

```bash
# 初始化数据库
mysql -u root -p < database/init.sql

# 启动 Redis
redis-server --daemonize yes

# 启动 Elasticsearch
./bin/elasticsearch -d
```

#### 2. 启动后端

```bash
cd backend

# 修改数据库、Redis、ES 连接信息（如果需要）
# 编辑 src/main/resources/application.yml

# 编译运行
mvn spring-boot:run
```

后端跑起来后监听 `8080` 端口。

#### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器监听 `5173` 端口，打开浏览器访问即可。

## 接口速览

所有接口统一前缀 `/api`，认证方式 Bearer Token（JWT）。

| 模块 | 接口 | 说明 |
|------|------|------|
| 认证 | POST `/auth/login` | 登录，返回 Token |
| 认证 | POST `/auth/register` | 注册 |
| 对话 | POST `/chat/stream` | 流式对话（SSE） |
| 对话 | POST `/chat/send` | 普通对话 |
| 对话 | GET `/chat/history` | 历史记录 |
| 知识库 | POST `/kb/create` | 创建知识库 |
| 知识库 | GET `/kb/list` | 知识库列表 |
| 文档 | POST `/document/upload` | 上传文档 |
| 文档 | GET `/document/list` | 文档列表 |

详细接口文档看 `docs/api.md`。

## 架构简图

```
浏览器 → Nginx(:80) → Spring Boot(:8080) → MySQL / Redis / Elasticsearch
                              ↓
                       阿里云百炼 LLM API
```

## License

MIT