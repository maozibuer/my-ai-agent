# API 接口文档

## 基础信息

| 项目 | 说明 |
|------|------|
| 基础地址 | `http://localhost:8080/api` |
| 数据格式 | JSON |
| 认证方式 | Bearer Token (JWT) |
| 字符编码 | UTF-8 |

## 通用说明

### 请求头

除登录和注册接口外，所有接口请求头需携带 JWT Token：

```
Authorization: Bearer <token>
Content-Type: application/json
```

### 统一响应格式

所有接口返回统一格式的 JSON 响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 状态码：200-成功，400-参数错误，401-未认证，403-无权限，500-服务器错误 |
| message | String | 响应消息 |
| data | Object | 响应数据 |

### 错误码说明

| 状态码 | 说明 |
|--------|------|
| 200 | 操作成功 |
| 400 | 请求参数错误 |
| 401 | 未认证或Token过期 |
| 403 | 无权限访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 1. 认证模块 (Auth)

### 1.1 用户登录

**POST** `/api/auth/login`

用户通过用户名和密码登录系统，获取 JWT Token。

**请求参数：**

```json
{
  "username": "admin",
  "password": "admin123"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 明文密码 |

**响应示例：**

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTcxNDQwMDAwMCwiZXhwIjoxNzE0NDg2NDAwfQ.signature",
    "userInfo": {
      "id": 1,
      "username": "admin",
      "email": "admin@ai-agent.com",
      "avatar": null,
      "role": "ADMIN"
    }
  }
}
```

### 1.2 用户注册

**POST** `/api/auth/register`

注册新用户账户。

**请求参数：**

```json
{
  "username": "newuser",
  "password": "password123",
  "email": "newuser@example.com"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名（3-64字符，唯一） |
| password | String | 是 | 密码（6-128字符） |
| email | String | 否 | 邮箱地址 |

**响应示例：**

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "id": 2,
    "username": "newuser",
    "email": "newuser@example.com",
    "role": "USER"
  }
}
```

### 1.3 用户登出

**POST** `/api/auth/logout`

登出当前用户，使 Token 失效。

**请求头：** 需携带 Authorization Token

**响应示例：**

```json
{
  "code": 200,
  "message": "登出成功",
  "data": null
}
```

### 1.4 获取当前用户信息

**GET** `/api/auth/info`

获取当前登录用户的详细信息。

**请求头：** 需携带 Authorization Token

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "username": "admin",
    "email": "admin@ai-agent.com",
    "avatar": null,
    "role": "ADMIN"
  }
}
```

---

## 2. 对话模块 (Chat)

### 2.1 发送消息（普通模式）

**POST** `/api/chat/send`

发送消息并获取 AI 助手的回复（非流式，等待完整响应后返回）。

**请求参数：**

```json
{
  "sessionId": "sess-550e8400-e29b-41d4-a716-446655440000",
  "message": "什么是机器学习？"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | 否 | 会话ID，为空则创建新会话 |
| message | String | 是 | 用户消息内容 |

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "recordId": 1,
    "sessionId": "sess-550e8400-e29b-41d4-a716-446655440000",
    "userMessage": "什么是机器学习？",
    "assistantMessage": "机器学习是人工智能的一个分支，它使计算机系统能够从数据中学习并改进...",
    "createTime": "2024-05-01T10:30:00"
  }
}
```

### 2.2 流式对话（SSE）

**POST** `/api/chat/stream`

发送消息并通过 Server-Sent Events (SSE) 流式返回 AI 助手的回复，实现逐字输出效果。

**请求参数：**

```json
{
  "sessionId": "sess-550e8400-e29b-41d4-a716-446655440000",
  "message": "请解释什么是深度学习"
}
```

**响应格式（SSE 事件流）：**

```
data: {"type":"start","sessionId":"sess-550e8400-e29b-41d4-a716-446655440000"}

data: {"type":"content","content":"深度"}

data: {"type":"content","content":"学习"}

data: {"type":"content","content":"是"}

data: {"type":"content","content":"机器学习"}

...

data: {"type":"end","recordId":2,"sessionId":"sess-550e8400-e29b-41d4-a716-446655440000"}
```

| 事件类型 | 说明 |
|----------|------|
| start | 流开始，返回 sessionId |
| content | 内容片段，包含生成的文本块 |
| end | 流结束，返回记录ID和会话ID |
| error | 错误信息 |

### 2.3 获取对话历史

**GET** `/api/chat/history?sessionId={sessionId}&page=1&size=20`

获取指定会话的对话历史记录。

**路径参数 / 查询参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | 是 | 会话ID |
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 20 |

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "total": 15,
    "page": 1,
    "size": 20,
    "records": [
      {
        "id": 1,
        "sessionId": "sess-550e8400-e29b-41d4-a716-446655440000",
        "userMessage": "什么是机器学习？",
        "assistantMessage": "机器学习是人工智能的一个分支...",
        "createTime": "2024-05-01T10:30:00"
      }
    ]
  }
}
```

### 2.4 删除会话

**DELETE** `/api/chat/session/{sessionId}`

删除指定会话及其所有聊天记录（逻辑删除）。

**路径参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | String | 是 | 会话ID |

**响应示例：**

```json
{
  "code": 200,
  "message": "会话已删除",
  "data": null
}
```

---

## 3. 知识库模块 (Knowledge Base)

### 3.1 创建知识库

**POST** `/api/kb/create`

创建新的知识库。

**请求参数：**

```json
{
  "name": "技术文档库",
  "description": "存储技术研发相关的文档资料",
  "department": "技术部"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 知识库名称（1-128字符） |
| description | String | 否 | 知识库描述 |
| department | String | 否 | 所属部门 |

**响应示例：**

```json
{
  "code": 200,
  "message": "知识库创建成功",
  "data": {
    "id": 1,
    "name": "技术文档库",
    "description": "存储技术研发相关的文档资料",
    "department": "技术部",
    "documentCount": 0,
    "createTime": "2024-05-01T10:00:00"
  }
}
```

### 3.2 知识库列表

**GET** `/api/kb/list?page=1&size=10&keyword=技术`

获取知识库分页列表，支持关键字搜索。

**查询参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |
| keyword | String | 否 | 搜索关键字（名称/描述） |

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "total": 5,
    "page": 1,
    "size": 10,
    "records": [
      {
        "id": 1,
        "name": "技术文档库",
        "description": "存储技术研发相关的文档资料",
        "department": "技术部",
        "documentCount": 12,
        "createTime": "2024-05-01T10:00:00"
      }
    ]
  }
}
```

### 3.3 知识库详情

**GET** `/api/kb/{id}`

获取指定知识库的详细信息。

**路径参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 知识库ID |

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "name": "技术文档库",
    "description": "存储技术研发相关的文档资料",
    "department": "技术部",
    "documentCount": 12,
    "createTime": "2024-05-01T10:00:00",
    "updateTime": "2024-05-01T12:00:00"
  }
}
```

### 3.4 更新知识库

**PUT** `/api/kb/update`

更新知识库信息。

**请求参数：**

```json
{
  "id": 1,
  "name": "技术文档库（更新）",
  "description": "更新后的描述",
  "department": "研发中心"
}
```

**响应示例：**

```json
{
  "code": 200,
  "message": "更新成功",
  "data": null
}
```

### 3.5 删除知识库

**DELETE** `/api/kb/{id}`

删除知识库（逻辑删除），关联文档同时标记删除。

**路径参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 知识库ID |

**响应示例：**

```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

---

## 4. 文档模块 (Document)

### 4.1 上传文档

**POST** `/api/document/upload`

上传文档到指定知识库，支持 PDF、DOCX、TXT、MD 格式。文件上传后异步处理：文本提取 -> 分块 -> 向量化 -> 存入 Elasticsearch。

**请求格式：** `multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| knowledgeBaseId | Long | 是 | 目标知识库ID |
| file | File | 是 | 文件（最大 100MB） |

**请求示例（cURL）：**

```bash
curl -X POST http://localhost:8080/api/document/upload \
  -H "Authorization: Bearer <token>" \
  -F "knowledgeBaseId=1" \
  -F "file=@/path/to/document.pdf"
```

**响应示例：**

```json
{
  "code": 200,
  "message": "文档上传成功，正在处理中",
  "data": {
    "id": 1,
    "fileName": "document.pdf",
    "fileType": "PDF",
    "fileSize": 1048576,
    "status": "PENDING",
    "createTime": "2024-05-01T10:00:00"
  }
}
```

### 4.2 文档列表

**GET** `/api/document/list?knowledgeBaseId={id}&page=1&size=20`

获取指定知识库下的文档列表。

**查询参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| knowledgeBaseId | Long | 是 | 知识库ID |
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 20 |

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "total": 3,
    "page": 1,
    "size": 20,
    "records": [
      {
        "id": 1,
        "fileName": "技术规范.pdf",
        "fileType": "PDF",
        "knowledgeBaseId": 1,
        "chunkCount": 25,
        "fileSize": 1048576,
        "status": "COMPLETED",
        "createTime": "2024-05-01T10:00:00"
      },
      {
        "id": 2,
        "fileName": "产品设计.docx",
        "fileType": "DOCX",
        "knowledgeBaseId": 1,
        "chunkCount": 0,
        "fileSize": 524288,
        "status": "PROCESSING",
        "createTime": "2024-05-01T10:05:00"
      }
    ]
  }
}
```

**文档状态说明：**

| 状态 | 说明 |
|------|------|
| PENDING | 等待处理 |
| PROCESSING | 正在处理（提取/分块/向量化） |
| COMPLETED | 处理完成 |
| FAILED | 处理失败 |

### 4.3 查询文档处理状态

**GET** `/api/document/{id}/status`

查询单个文档的处理状态和进度。

**路径参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 文档ID |

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 2,
    "fileName": "产品设计.docx",
    "status": "COMPLETED",
    "chunkCount": 18
  }
}
```

### 4.4 删除文档

**DELETE** `/api/document/{id}`

删除文档，同时从 Elasticsearch 中移除相关索引数据。

**路径参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 文档ID |

**响应示例：**

```json
{
  "code": 200,
  "message": "文档删除成功",
  "data": null
}
```

---

## 5. 用户管理模块 (User)

> 需要管理员（ADMIN）角色权限

### 5.1 用户列表

**GET** `/api/user/list?page=1&size=10&keyword=`

获取系统用户分页列表。

**查询参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |
| keyword | String | 否 | 搜索关键字（用户名/邮箱） |

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "total": 2,
    "page": 1,
    "size": 10,
    "records": [
      {
        "id": 1,
        "username": "admin",
        "email": "admin@ai-agent.com",
        "role": "ADMIN",
        "createTime": "2024-05-01T08:00:00"
      },
      {
        "id": 2,
        "username": "newuser",
        "email": "newuser@example.com",
        "role": "USER",
        "createTime": "2024-05-01T09:00:00"
      }
    ]
  }
}
```

### 5.2 更新用户信息

**PUT** `/api/user/update`

更新用户信息（邮箱、头像、角色）。

**请求参数：**

```json
{
  "id": 2,
  "email": "updated@example.com",
  "avatar": "https://example.com/avatar.png",
  "role": "USER"
}
```

**响应示例：**

```json
{
  "code": 200,
  "message": "更新成功",
  "data": null
}
```

---

## 6. 操作日志模块 (Log)

> 需要管理员（ADMIN）角色权限

### 6.1 日志列表

**GET** `/api/log/list?page=1&size=20&userId=&operation=`

获取系统操作日志分页列表。

**查询参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 20 |
| userId | Long | 否 | 按用户ID筛选 |
| operation | String | 否 | 按操作描述筛选 |

**响应示例：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "total": 50,
    "page": 1,
    "size": 20,
    "records": [
      {
        "id": 1,
        "userId": 1,
        "operation": "用户登录",
        "method": "AuthController.login",
        "params": "{\"username\":\"admin\"}",
        "ip": "192.168.1.100",
        "time": 125,
        "createTime": "2024-05-01T08:00:00"
      }
    ]
  }
}
```

---

## 接口示例（Postman / cURL）

### 登录并获取 Token

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 使用 Token 发送聊天消息

```bash
curl -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{"sessionId":"sess-001","message":"你好"}'
```

### 流式对话（SSE）

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{"sessionId":"sess-001","message":"解释什么是RAG"}'
```

### 上传文档

```bash
curl -X POST http://localhost:8080/api/document/upload \
  -H "Authorization: Bearer <your_token>" \
  -F "knowledgeBaseId=1" \
  -F "file=@/path/to/document.pdf"
```
