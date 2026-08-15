# Deep Explore

一个最小化、可扩展的 AI Agent 对话项目。后端使用 Spring Boot 和
LangChain4j，前端使用 React、TypeScript 和 Vite。

## 项目结构

```text
.
├── deep_explore/           # Java 21 后端
├── deep_explore_frontend/  # React 前端
└── .env.example            # 后端环境变量示例
```

后端通过 `AgentService` 隔离具体 Agent 框架。当前实现是
`LangChain4jAgentService`，以后可以增加 LangGraph4j 实现，或通过 HTTP
调用独立的 Python LangGraph Runtime，而无需修改 Controller 和前端协议。

## 环境要求

- Java 21
- Node.js 20 或更高版本
- npm

本机已通过 Homebrew 安装 JDK 21，但系统默认仍是 Java 8。启动后端前执行：

```bash
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

## 配置模型

后端支持 OpenAI-compatible API。至少需要设置 API Key：

```bash
export AI_API_KEY="your-api-key"
export AI_BASE_URL="https://api.openai.com/v1"
export AI_MODEL_NAME="gpt-4.1-mini"
```

### 接入 DeepSeek V4

在 [DeepSeek Platform](https://platform.deepseek.com/api_keys) 创建 API Key 后，
以根目录 `.env.deepseek.example` 为模板配置根目录 `.env`：

```bash
cp .env .env.ollama.backup
cp .env.deepseek.example .env
```

然后仅编辑 `.env` 的这一行：

```dotenv
AI_API_KEY=sk-你的真实DeepSeekKey
```

DeepSeek 配置默认使用：

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

本机已安装 Ollama `0.32.6` 和轻量模型 `qwen3.5:0.8b`。仓库根目录的
`.env` 已配置为：

```bash
AI_API_KEY=ollama
AI_BASE_URL=http://127.0.0.1:11434/v1
AI_MODEL_NAME=qwen3.5:2b
AI_REASONING_EFFORT=none
AI_MAX_COMPLETION_TOKENS=1024
AI_DEEP_REASONING_EFFORT=low
AI_DEEP_MAX_COMPLETION_TOKENS=2048
```

Ollama 服务由 `/Applications/Ollama.app` 管理。查看或主动释放模型内存：

```bash
ollama list
ollama ps
ollama stop qwen3.5:0.8b
```

也可以基于 `.env.example` 创建本地 `.env`，然后加载：

```bash
set -a
source .env
set +a
```

不要把 `.env` 或 API Key 提交到 Git。

## 启动后端

```bash
cd deep_explore
./mvnw spring-boot:run
```

健康检查：

```bash
curl http://localhost:8080/api/health
```

没有配置 `AI_API_KEY` 时，服务仍可启动，但聊天接口会返回配置错误事件。

## 启动前端

另开一个终端：

```bash
cd deep_explore_frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。开发环境会把 `/api` 代理到
`http://localhost:8080`。

## 验证

```bash
cd deep_explore
./mvnw test

cd ../deep_explore_frontend
npm run build
npm run lint
```

## API

`POST /api/chat/stream` 接收：

```json
{
  "conversationId": null,
  "message": "你好",
  "mode": "FAST"
}
```

`mode` 支持：

- `FAST`：关闭思考，最多输出 1024 token，适合普通问答。
- `DEEP`：低强度思考，最多输出 2048 token，适合分析、规划和多步骤问题。

响应类型为 `text/event-stream`，当前事件包括：

- `metadata`：返回服务端生成的 `conversationId`
- `delta`：模型增量文本
- `done`：本轮完成
- `error`：配置、并发或模型调用错误

协议已为后续 `tool_start`、`tool_end` 和图节点事件预留扩展空间。
