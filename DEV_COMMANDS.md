# Deep Explore 命令速查

默认从项目根目录 `ai_agent_unified/` 执行。

## 首次初始化

```bash
# 配置模型密钥
cp .env.deepseek.example .env

# 安装前端和评测依赖
(cd deep_explore_frontend && npm install)
(cd evals && npm ci --omit=optional)

# 安装并启动 PostgreSQL
brew install postgresql@17
brew services start postgresql@17

# 创建账号、开发库和评测库
createuser deep_explore
createdb -O deep_explore deep_explore
createdb -O deep_explore deep_explore_eval
psql -d postgres -c "ALTER ROLE deep_explore WITH PASSWORD 'deep_explore';"
```

## 日常启动

终端一，启动后端 `http://localhost:8080`：

```bash
set -a
source .env
set +a
cd deep_explore
./mvnw spring-boot:run
```

终端二，启动前端 `http://127.0.0.1:5173`：

```bash
cd deep_explore_frontend
npm run dev
```

```bash
# 启动/停止 PostgreSQL
brew services start postgresql@17
brew services stop postgresql@17

# 检查后端
curl http://localhost:8080/api/health
curl http://localhost:8080/api/config
```

前端和后端使用 `Ctrl+C` 停止。

## Promptfoo 评测

终端一，使用独立评测库启动后端：

```bash
set -a
source .env
set +a
export DB_URL=jdbc:postgresql://localhost:5432/deep_explore_eval
cd deep_explore
./mvnw spring-boot:run
```

终端二，运行 FAST 模式的 10 个单轮问题：

```bash
set -a
source .env
set +a
cd evals
npm run validate
npm run eval:smoke
```

```bash
# 打开静态报告
open evals/reports/smoke.html

# 启动 Promptfoo 本地结果页
(cd evals && npm run view)

# 查看版本化基线
open evals/baselines/smoke-v1.md
```

报告位于 `evals/reports/smoke.html` 和 `evals/reports/smoke.json`。
存在失败用例时评测命令返回退出码 `100`，报告仍会生成。

## 测试与构建

```bash
# 后端测试
(cd deep_explore && ./mvnw test)

# PostgreSQL 集成测试
(cd deep_explore && ./mvnw -Dtest=ConversationPersistenceIT test)

# 前端检查
(cd deep_explore_frontend && npm run lint && npm run build)

# Promptfoo 配置和依赖检查
(cd evals && npm run validate && npm audit --omit=optional)
```

## PostgreSQL

```bash
# 可用性和服务状态
pg_isready -h localhost -p 5432
brew services info postgresql@17

# 连接开发库/评测库
psql -U deep_explore -d deep_explore
psql -U deep_explore -d deep_explore_eval

# 查看开发库表
psql -U deep_explore -d deep_explore -c '\dt'

# 查看最近 Agent Run
psql -U deep_explore -d deep_explore -c \
  "SELECT id, conversation_id, profile_id, status, created_at FROM agent_runs ORDER BY created_at DESC LIMIT 10;"

# 查看日志
tail -n 100 /opt/homebrew/var/log/postgresql@17.log
```

## 故障检查

```bash
# 端口占用
lsof -nP -iTCP:5432 -sTCP:LISTEN
lsof -nP -iTCP:8080 -sTCP:LISTEN
lsof -nP -iTCP:5173 -sTCP:LISTEN

# 相关进程
ps aux | rg 'spring-boot|vite|promptfoo|postgres'

# 工作区改动
git status --short
git diff --check
```
