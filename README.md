# Deep Explore

一个可扩展的 AI Agent 对话工作台。后端使用 Spring Boot、WebFlux 和
LangChain4j，前端使用 React、TypeScript、assistant-ui 和 Streamdown。

当前能力：

- 基于 HTTP SSE 的流式回答，以及随时停止和重新生成。
- 独立持久化 Run、断线事件回放、多观察者订阅和显式取消。
- `FAST` / `DEEP` 双模式与独立模型配置。
- 流式 Markdown、GFM 表格、代码高亮和代码复制。
- PostgreSQL 完整会话持久化、自动标题、搜索、重命名和删除。
- LangChain4j AI Services 与 ChatMemory 多轮上下文管理。
- 可选 Tavily `web_search` 与 LangChain4j ReAct 工具循环。
- Harness Run/Event/Checkpoint 持久化与强类型执行事件。
- Agent Executor Registry，支持按 `agentId` 扩展不同 Agent 实现。
- PostgreSQL owner-scoped 会话租约、服务重启恢复与浏览器旧历史自动迁移。
- LOCAL/JWT 双安全模式与 tenant/owner scoped 资源查询。
- 响应式桌面工作台、移动端会话抽屉和明暗主题。

## 项目结构

```text
.
├── deep_explore/             # Java 21 后端
├── deep_explore_frontend/    # React 19 前端
├── .env.example              # 通用 OpenAI-compatible 配置示例
└── .env.deepseek.example     # DeepSeek V4 配置示例
```

后端由 `ChatStreamService` 将现有 HTTP/SSE 协议映射到 Harness，
`HarnessOrchestrator` 负责高层编排，`RunSession` 管理单次运行生命周期。
当前执行器是 `LangChain4jAgentExecutor`，以后可以增加 LangGraph4j 实现，
或通过 HTTP 调用独立的 Python LangGraph Runtime，而无需修改前端协议。

新 Agent 应实现 `AgentExecutor` 并注册到 `AgentExecutorRegistry`。完整边界和运行流程见
[`docs/architecture/agent-harness.md`](docs/architecture/agent-harness.md)。

## 环境要求

- Java 21
- Node.js 20 或更高版本
- npm
- PostgreSQL 15 或更高版本（也可使用 Docker Compose）

本机已通过 Homebrew 安装 JDK 21，但系统默认仍是 Java 8。启动后端前执行：

```bash
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

## 配置模型

后端支持 DeepSeek、Ollama 和其他 OpenAI-compatible API。至少需要设置：

```dotenv
AI_PROVIDER=OPENAI_COMPATIBLE
AI_API_KEY=your-api-key
AI_BASE_URL=https://api.openai.com/v1
AI_MODEL_NAME=gpt-4.1-mini
AI_DEEP_MODEL_NAME=gpt-4.1
```

### 接入 DeepSeek V4

在 [DeepSeek Platform](https://platform.deepseek.com/api_keys) 创建 API Key 后，
以 `.env.deepseek.example` 为模板创建根目录 `.env`：

```bash
cp .env.deepseek.example .env
```

然后仅编辑 `.env` 的这一行：

```dotenv
AI_API_KEY=sk-你的真实DeepSeekKey
```

示例配置使用：

- `deepseek-v4-flash`：快速模式，关闭思考。
- `deepseek-v4-pro`：深度模式，开启 `high` 思考强度。

### 可选 Web Search

`web_search` 默认使用 Tavily。配置 Key 后启用：

```dotenv
WEB_SEARCH_ENABLED=true
WEB_SEARCH_PROVIDER=TAVILY
TAVILY_API_KEY=tvly-你的真实TavilyKey
```

后端只向模型注册一个 `web_search` Tool。请求参数 `searchProvider` 可传
`JINA` 或 `TAVILY`，未传时使用 `WEB_SEARCH_PROVIDER`。模型只在需要实时、
时效性或外部验证信息时调用搜索，并在最终回答中引用来源 URL。

工具调用过程会通过同一个 SSE 响应的 `tool_start`、`tool_end` 事件实时发送
到前端。前端在助手消息内显示可折叠的执行时间线；刷新会话后，通过持久化的
Run Events 恢复该时间线。浏览器只接收白名单摘要，不接收完整工具结果。

中国大陆网络如无法解析 `s.jina.ai`，可设置
`JINA_SEARCH_BASE_URL=https://s.jinaai.cn/` 并配置 `JINA_API_KEY`。

API Key 只应保存在被 Git 忽略的 `.env` 文件、部署平台密钥库或密码管理器中。
不要写入 `application.yml`、`.env.example`、前端环境变量或提交记录。

验证 API Key 与余额：

```bash
set -a
source .env
set +a

curl https://api.deepseek.com/user/balance \
  -H "Authorization: Bearer $AI_API_KEY"
```

### 接入 Ollama

先启动 Ollama 并下载本地模型，再在根目录 `.env` 中配置：

```dotenv
AI_PROVIDER=OLLAMA
AI_API_KEY=ollama
AI_BASE_URL=http://127.0.0.1:11434/v1
AI_MODEL_NAME=qwen3.5:2b
AI_DEEP_MODEL_NAME=qwen3.5:2b
AI_REASONING_EFFORT=none
AI_MAX_COMPLETION_TOKENS=1024
AI_DEEP_REASONING_EFFORT=low
AI_DEEP_MAX_COMPLETION_TOKENS=2048
```

查看或主动释放模型内存：

```bash
ollama list
ollama ps
ollama stop qwen3.5:2b
```

也可以基于 `.env.example` 创建本地 `.env`，然后加载：

```bash
set -a
source .env
set +a
```

不要把 `.env` 或 API Key 提交到 Git。

### 安全模式

本地开发默认使用显式 `LOCAL` 模式。生产环境应启用 JWT：

```dotenv
SECURITY_MODE=JWT
SECURITY_TENANT_ID=tenant-a
SECURITY_OWNER_ID=expected-jwt-subject
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://issuer.example.com
```

JWT subject 必须与 `SECURITY_OWNER_ID` 一致。Conversation 和 Workspace
仓储查询同时使用 tenant/owner 条件；CORS 不是认证机制。

## 启动 PostgreSQL

使用仓库内的 Compose 配置：

```bash
docker compose up -d postgres
```

默认连接信息：

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/deep_explore
DB_USERNAME=deep_explore
DB_PASSWORD=deep_explore
```

也可以连接已有 PostgreSQL。后端启动时由 Flyway 自动创建和升级表结构。

## 启动后端

```bash
cd deep_explore
./mvnw spring-boot:run
```

健康检查：

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/config
```

没有配置 `AI_API_KEY` 时，服务仍可启动，但聊天接口会返回配置错误事件。
`/api/config` 只公开 Provider 与模型名，不会返回 API Key。

## 启动前端

另开一个终端：

```bash
cd deep_explore_frontend
npm install
npm run dev
```

访问 `http://localhost:5173` 或 `http://127.0.0.1:5173`。开发环境会把 `/api` 代理到
`http://localhost:8080`。

完整会话历史存放在 PostgreSQL 的 `conversations` 和 `messages` 表中；
发送给模型的有界工作记忆由 LangChain4j `ChatMemory` 管理，并持久化到
`conversation_memory`。失败或取消后会标记工作记忆为 dirty，下一轮从完整
会话历史重建。主题仍存放在 Local Storage 中。

升级后的首次加载会把旧版 IndexedDB 会话导入 PostgreSQL。导入成功前不会
删除浏览器原数据，导入标记保存在 Local Storage 中。

## 验证

```bash
cd deep_explore
./mvnw test

# Docker 可用时运行 PostgreSQL V2 集成测试
./mvnw -Dtest=ConversationPersistenceIT test

cd ../deep_explore_frontend
npm run build
npm run lint
```

## API

前端默认使用独立 Run API：

- `POST /api/runs`：创建后台 Run，返回 `202` 和 `runId`
- `GET /api/runs/{runId}`：查询 Run 状态
- `GET /api/runs/{runId}/events?afterSequence=N`：回放并订阅 SSE
- `POST /api/runs/{runId}/cancel`：显式取消

`POST /api/chat/stream` 继续作为兼容接口。请求接收：

```json
{
  "conversationId": "服务端会话 ID",
  "message": "你好",
  "mode": "FAST",
  "userMessageId": "前端用户消息 ID",
  "userParentMessageId": "上一条消息 ID",
  "assistantMessageId": "待生成的助手消息 ID"
}
```

请求还可传 `agentId` 和 `profileId`。未传时使用服务端默认 Agent 与
`FAST`/`DEEP` 兼容模式。

会话管理接口：

- `GET/POST /api/conversations`
- `GET/PATCH/DELETE /api/conversations/{id}`
- `GET /api/conversations/{id}/messages`
- `GET /api/conversations/{id}/run-activities`
- `POST /api/conversations/import`

`mode` 支持：

- `FAST`：低延迟模式，适合普通问答。
- `DEEP`：深度推理模式，适合分析、规划和多步骤问题。

模型和 token 上限由 `.env` 中的 `AI_MODEL_NAME`、
`AI_DEEP_MODEL_NAME`、`AI_MAX_COMPLETION_TOKENS` 与
`AI_DEEP_MAX_COMPLETION_TOKENS` 控制。

响应类型为 `text/event-stream`，当前事件包括：

- `metadata`：返回服务端生成的 `conversationId`
- `tool_start`：工具名称、Provider 和安全处理后的任务摘要
- `tool_end`：工具成功或失败状态
- `delta`：模型增量文本
- `done`：本轮完成
- `error`：配置、并发或模型调用错误

每个事件还携带 `runId`、`sequence`、`occurredAt` 和可选的
`assistantMessageId`，前端据此将工具活动绑定到正确的助手消息。
