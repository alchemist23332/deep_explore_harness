# 在 Windows 上运行 Deep Explore

仓库自带的 `DEV_COMMANDS.md` 和 `scripts/` 是为 macOS（Homebrew + Colima）写的，
在 Windows 上不能直接照抄。本文是 Windows 原生路径的运行手册，**所有步骤已于
2026-09-10 在本机实际验证通过**（含两个必须的代码/配置修复，见第 6 节）。

---

## 1. 与 macOS 文档的差异

| 项目 | macOS 文档的做法 | Windows 上的做法 |
|---|---|---|
| 容器运行时 | Colima 虚拟机 + `socat` 桥 | Docker Desktop（WSL2 后端），不需要桥 |
| Docker 连接地址 | `tcp://127.0.0.1:23750` | `npipe:////./pipe/dockerDesktopLinuxEngine` |
| PostgreSQL | `brew install postgresql@17` | 用仓库自带的 `compose.yml` 起容器 |
| compose 命令 | `docker compose` | `docker-compose`（本机装的是独立版 v5.0.2，插件不可用） |
| 环境变量脚本 | `source ./scripts/docker-env.sh` | 不要执行，脚本里是 Colima 硬编码路径 |
| Maven | `./mvnw` | `./mvnw`（Git Bash），见第 6.2 节的路径转换坑 |

`scripts/docker-env.sh` 和 `scripts/docker-runtime.sh` 全程不要执行。前者会把
`DOCKER_HOST` 指向 `.rt/.colima/default/docker.sock`，后者调用 `colima` 命令，
Windows 上必然失败。

另外 `DEV_COMMANDS.md` 开头写「默认从项目根目录 `ai_agent_unified/` 执行」，
与当前仓库名 `deep_explore_harness` 不一致，属文档遗留，忽略即可。

---

## 2. 前置条件

| 组件 | 要求 | 本机状态（2026-09-10） |
|---|---|---|
| Java | 21（pom 要求） | 21.0.5 在 PATH（`D:\develop\jdk21`），`JAVA_HOME` 未设也可用 |
| Node / npm | ≥ 22.22 / 任意 | v22.22.2 / 10.9.3 |
| Docker Desktop | Linux 容器模式 | 29.2.1，管道 `npipe:////./pipe/dockerDesktopLinuxEngine` |
| PostgreSQL | 无需本机安装 | 用 `compose.yml` 容器 |
| 模型 API Key | 必需 | DeepSeek，配置在 `.env` |

全局 `mvn`（`D:\develop\apache-maven-3.9.4`）在带路径转换限制的终端里会报错，
一律使用项目自带的 `./mvnw`。

---

## 3. 启动步骤

以下命令在 **Git Bash**、项目根目录 `D:\deep_explore_harness` 下执行。

### 第 1 步：启动 Docker Desktop

手动打开 Docker Desktop 等待就绪，确认：

```bash
docker version
docker context inspect --format '{{.Endpoints.docker.Host}}'
# 预期输出 npipe:////./pipe/dockerDesktopLinuxEngine
```

### 第 2 步：配置 `.env`

```bash
cp .env.deepseek.example .env   # 首次
```

至少确认/修改：

```dotenv
AI_API_KEY=sk-你的真实Key
SANDBOX_DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine
SANDBOX_DATA_DIR=../.deep-explore-data    # 保持相对路径不动
```

> `.env` 必须是 **LF** 换行。CRLF 会让 `source .env` 把 `\r` 带进变量值。
> `SANDBOX_DATA_DIR` 相对后端工作目录（`deep_explore/`）解析，**后端必须从
> `deep_explore/` 目录启动**，否则数据目录会跑到别处。

### 第 3 步：启动 PostgreSQL

```bash
docker-compose up -d postgres
docker-compose ps          # 等状态变 healthy
```

`compose.yml` 的库/用户/密码（均为 `deep_explore`）与 `.env` 默认值一致，
无需建库建用户。

### 第 4 步：构建沙箱镜像（仅首次，约 4 分钟）

```bash
docker build -t deep-explore/sandbox-fullstack:v1 sandbox/java21
```

镜像名必须与 `.env` 里的 `SANDBOX_FULLSTACK_IMAGE` 一致。

### 第 5 步：安装前端依赖（仅首次）

```bash
cd deep_explore_frontend && npm install
```

### 第 6 步：启动后端（终端一）

```bash
cd /d/deep_explore_harness
set -a && source .env && set +a
cd deep_explore
unset MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL   # 见 6.2，普通 Git Bash 无需此行
./mvnw spring-boot:run
```

首次运行 `mvnw` 会下载 Maven 3.9.15 及全部依赖（已配阿里云镜像，约几分钟）。
启动成功标志：日志出现 `Started DeepExploreApplication`，Flyway 自动执行
V1–V9 建表。

### 第 7 步：启动前端（终端二）

```bash
cd /d/deep_explore_harness/deep_explore_frontend
npm run dev
```

> Vite 8 在本机只监听 IPv6 的 `[::1]:5173`（Node 17+ 把 `localhost` 解析成
> `::1`），**必须用 `http://localhost:5173` 访问，`127.0.0.1:5173` 连不上**。
> 在无终端的后台环境里 `npm run dev` 会静默退出，此时改用：
> `node node_modules/vite/bin/vite.js --clearScreen false`。

### 第 8 步：打开页面

<http://localhost:5173/chat>

---

## 4. 启动后验证

```bash
# 注意：若终端设置了 http_proxy 等代理变量，本地测试要加 --noproxy "*"

curl -s --noproxy "*" http://localhost:8080/api/health
# {"status":"ok"}

curl -s --noproxy "*" http://localhost:8080/api/runtime-profiles
# available:true, message:"Docker is available"

curl -s --noproxy "*" http://localhost:8080/api/config
# 显示 provider、模型名、profiles

docker ps -a --filter label=deep-explore.managed=true
# 工作区容器列表
```

端到端冒烟（已在 2026-09-10 验证通过）：

```bash
# 1. 创建工作区
curl -s --noproxy "*" -X POST http://localhost:8080/api/workspaces \
  -H "Content-Type: application/json" \
  -d '{"name":"smoke-test","runtimeProfile":"FULLSTACK","starterTemplate":"WEB_TYPESCRIPT"}'

# 2. 启动工作区（用返回的 id）
curl -s --noproxy "*" -X POST http://localhost:8080/api/workspaces/<id>/start

# 3. 容器内执行命令
curl -s --noproxy "*" -X POST http://localhost:8080/api/workspaces/<id>/commands \
  -H "Content-Type: application/json" -d '{"command":"java -version && node -v"}'

# 4. AI 链路（SSE）
curl -s --noproxy "*" -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"1+1=?","mode":"FAST"}'
```

---

## 5. 停止

```bash
# 后端、前端：各自终端 Ctrl+C
docker-compose stop postgres
```

`.deep-explore-data/` 是工作区文件的唯一真源，停容器不丢代码。

---

## 6. 已解决的问题与已知问题

### 6.1 【已修复，代码已改】docker-java 连不上 Docker（关键修复）

**现象**：后端启动正常，但 `/api/runtime-profiles` 报
`"Docker is unavailable"`，日志里是
`HttpHostConnectException: Connect to npipe://localhost:2375 failed: Connection refused`。

**根因**：Spring Boot 3.5.16 的 BOM 把 httpclient5 管理到 **5.5.2**，而
docker-java 3.4.2 官方声明的是 5.0.3。httpclient5 5.4+ 重写了
`DefaultHttpClientConnectionOperator`：普通（非 TLS）连接**不再经过
`ConnectionSocketFactory`**，直接创建 TCP socket 连接。docker-java 为 npipe/unix
注册的自定义 socket 工厂被完全绕过，于是真的去连 `localhost:2375` 并被拒绝。

**修复**（已提交到 `deep_explore/pom.xml`）：

```xml
<properties>
    <!-- docker-java 3.4.2's npipe/unix transports register a custom
         ConnectionSocketFactory. HttpClient 5.4+ bypasses socket factories
         for plain connections ... Pin to the last compatible 5.3.x line. -->
    <httpclient5.version>5.3.1</httpclient5.version>
</properties>
```

验证方法：用项目 classpath 跑最小 ping 程序，5.5.2 必失败、5.3.1 成功。
升 docker-java 到 ≥3.4.x 更新版本时需重新评估此 pin。

### 6.2 【环境坑】Git Bash 里 `./mvnw` 报 classworlds ClassNotFound

**现象**：`./mvnw` 或全局 `mvn` 报
`找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher`。

**根因**：某些沙箱化终端（如 WorkBuddy 内置 shell）设置了
`MSYS_NO_PATHCONV=1` 和 `MSYS2_ARG_CONV_EXCL=*`，禁用了 Git Bash 的
POSIX→Windows 路径自动转换。mvn 脚本把 `/c/Users/...` 形式的 classpath 原样
传给 `java.exe`，Java 找不到 jar。**全局 `mvn 3.9.4` 本身没坏**，普通
Git Bash 里可以正常用。

**修复**：运行前 `unset MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL`。

### 6.3 【已修复，代码已改】浏览器终端"未连接"（连接后 ~100ms 闪断）

**症状**：工作区 Terminal 工具栏短暂经过"正在启动/正在连接"后回到未连接/已断开，
无法输入任何命令；后端 `/api/workspaces`、`/api/runtime-profiles` 一切正常。

**根因**（与 Windows 无关，是后端代码 bug，全平台复现）：
`TerminalWebSocketHandler` 把入站 `WebSocketMessage` 整个交给
`blocking.mono(...)`（切换到 docker 线程池）之后才读取 payload。WebFlux 入站
WS 消息底层是 Netty pooled buffer，**只在 `onNext` 期间有效**——池线程执行
`getPayloadAsText()` 时 `refCnt` 已经归零，抛
`IllegalReferenceCountException: refCnt: 0` → receiver 错误 → `firstWithSignal`
取消 sender → 终端会话被 `doFinally` 关闭 → WS 闪断。而前端 `XtermTerminal`
在 `onopen` 里立刻发送 `resize` 消息，**第一条消息必杀会话**。

**修复**（`deep_explore/src/main/java/.../workspace/adapter/in/web/TerminalWebSocketHandler.java`）：
在 `onNext` 内同步把消息快照为不可变的 `TerminalClientFrame`（text 或 byte[]），
再交给池线程：
```java
.concatMap(message -> {
    TerminalClientFrame frame = snapshot(message);   // 在 Netty 线程上拷贝 payload
    return blocking.mono(BlockingExecution.Kind.DOCKER, () -> {
        handleMessage(terminal, frame);
        return true;
    });
})
```

**次生问题（同轮修复）**：`DockerSandboxTerminal` 原用 `PipedInputStream` 做 exec
stdin，其"读写端线程存活检查"（Write/Read end dead）会被调度池线程的 60s 闲置回收
触发——终端闲置 1 分钟后第一次敲键就会死。已替换为自写的
`ByteQueueInputStream`（`LinkedBlockingQueue` 实现，无线程亲和性）。

**验证方法**（Node 22 内置 WebSocket 即可协议级复现，无需浏览器）：
```js
const ws = new WebSocket('ws://localhost:8080/api/workspaces/<id>/terminal')
// 修复前：ready → prompt → ~100ms 后 close(1005/1006)，输入无回显
// 修复后：ready → prompt → echo 命令完整回显；闲置 70s 后再敲键依然存活
```

**附带说明**：`deep_explore_frontend/vite.config.ts` 的 `proxy['/api']` 也补了
`ws: true`（Vite 8 把 proxy 的 WS 转发改成了 opt-in）。对本项目是旁路修复——
浏览器终端 WS 按 `VITE_API_BASE_URL` 直连 8080，不走 Vite 代理——但若前端配置了
空 `VITE_API_BASE_URL` 走相对路径，则需要它。

### 6.4 【已知限制】Promptfoo 评测在 Windows 上装不上

`evals/package.json` 依赖 `@libsql/darwin-arm64`（macOS Apple Silicon 专用），
Windows 上 `npm ci` 会失败。`evals/` 与项目本体运行无关，跳过。

### 6.5 【小坑】Vite 只监听 IPv6

见第 7 步说明：访问用 `http://localhost:5173`，不是 `127.0.0.1`。

### 6.6 【小坑】沙箱目录是 Windows 路径挂进 Linux 容器

`.deep-explore-data/workspaces/{id}/files` 从 `D:\` 挂载进容器（Docker Desktop
支持，但 IO 较慢）。遇到权限怪问题优先怀疑这里。

---

## 7. 排错速查

| 现象 | 原因与处理 |
|---|---|
| 后端报连不上 5432 | Postgres 容器没起或未 healthy：`docker-compose ps` |
| `Docker is unavailable` | 确认 pom 里 httpclient5 pin 还在（6.1）、`SANDBOX_DOCKER_HOST` 正确、Docker Desktop 在跑 |
| mvn 报 classworlds ClassNotFound | `unset MSYS_NO_PATHCONV MSYS2_ARG_CONV_EXCL`（6.2） |
| 创建容器失败 | 镜像没构建或名字与 `SANDBOX_FULLSTACK_IMAGE` 不一致 |
| `.env` 变量值尾部带怪字符 | `.env` 是 CRLF 换行，转成 LF |
| curl 本地端口返回 502 | 终端有 `http_proxy`，加 `--noproxy "*"` |
| 前端打不开 | 用 `http://localhost:5173`（IPv6），不是 127.0.0.1 |
| `docker compose` 报 unknown command | 本机只有独立版，用 `docker-compose` |
| 终端连接后 ~100ms 闪断、"未连接" | 后端 WS 消息 buffer 跨线程失效 bug，见 6.3（已修复） |
| 文件树报 "Unable to inspect workspace file" | 容器内 `npm install` 创建的 `node_modules/.bin` symlink 在 Windows 宿主机上是 NIO 无法 stat 的重解析点；已改为跳过 stat 失败的条目（`LocalWorkspaceStorage.tryToEntry`，2026-09-11 修复） |
| 沙箱里 `npm install` 一律 exit 137 | `SANDBOX_COMMAND_TIMEOUT` 默认 60s 太短：Windows 绑定挂载下 IO 慢，npm 可能超时被 SIGKILL。把 `.env` 里的 `SANDBOX_COMMAND_TIMEOUT` 调到 `180s`；命令调用不再按次数限制。 |
| 终端跑 `npm run dev`，预览面板无内容 | preview 面板只展示 `start_preview` 工具启动的进程（容器内 `0.0.0.0:3000`），终端自己跑的进程永远不显示；Vite dev 监听 5173 且绑 loopback，与 preview 端口映射 3000 也不匹配。让 AI 跑 `npm run build` + `start_preview` 才是正路 |

```bash
netstat -ano | grep -E ":5432|:8080|:5173"          # 端口占用
docker-compose logs postgres                          # Postgres 日志
docker ps -a --filter label=deep-explore.managed=true # 沙箱容器
docker exec -it deep_explore_harness-postgres-1 \
  psql -U deep_explore -d deep_explore -c '\dt'      # 数据库表
```
