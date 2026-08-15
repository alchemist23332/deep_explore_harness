# Deep Explore

一个可扩展的 AI Agent 对话工作台。后端使用 Spring Boot、WebFlux 和
LangChain4j，前端使用 React、TypeScript、assistant-ui 和 Streamdown。

当前能力：

- 基于 HTTP SSE 的流式回答，以及随时停止和重新生成。
- `FAST` / `DEEP` 双模式与独立模型配置。
- 流式 Markdown、GFM 表格、代码高亮和代码复制。
- PostgreSQL 完整会话持久化、自动标题、搜索、重命名和删除。
- LangChain4j AI Services 与 ChatMemory 多轮上下文管理。
- Harness Run/Event/Checkpoint 持久化与强类型执行事件。
- Agent Executor Registry，支持按 `agentId` 扩展不同 Agent 实现。
- PostgreSQL 会话生成锁、服务重启恢复与浏览器旧历史自动迁移。
- 响应式桌面工作台、移动端会话抽屉和明暗主题。

## 项目结构

```text
.
├── deep_explore/             # Java 21 后端
├── deep_explore_frontend/    # React 19 前端
├── .env.example              # 通用 OpenAI-compatible 配置示例
└── .env.deepseek.example     # DeepSeek V4 配置示例
```

后端通过 `AgentService` 兼容门面保持现有 API，由 `HarnessOrchestrator`
管理运行生命周期。当前执行器是 `LangChain4jAgentExecutor`，以后可以增加
LangGraph4j 实现，或通过 HTTP 调用独立的 Python LangGraph Runtime，而无需
修改 Controller 和前端协议。

当前 `AgentService` 已作为兼容门面委托给 Harness。新 Agent 应实现
`AgentExecutor` 并注册到 `AgentExecutorRegistry`。完整边界和运行流程见
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
`conversation_memory`。主题仍存放在 Local Storage 中。

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

`POST /api/chat/stream` 接收：

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

会话管理接口：

- `GET/POST /api/conversations`
- `GET/PATCH/DELETE /api/conversations/{id}`
- `GET /api/conversations/{id}/messages`
- `POST /api/conversations/import`

`mode` 支持：

- `FAST`：低延迟模式，适合普通问答。
- `DEEP`：深度推理模式，适合分析、规划和多步骤问题。

模型和 token 上限由 `.env` 中的 `AI_MODEL_NAME`、
`AI_DEEP_MODEL_NAME`、`AI_MAX_COMPLETION_TOKENS` 与
`AI_DEEP_MAX_COMPLETION_TOKENS` 控制。

响应类型为 `text/event-stream`，当前事件包括：

- `metadata`：返回服务端生成的 `conversationId`
- `delta`：模型增量文本
- `done`：本轮完成
- `error`：配置、并发或模型调用错误

协议已为后续 `tool_start`、`tool_end` 和图节点事件预留扩展空间。
