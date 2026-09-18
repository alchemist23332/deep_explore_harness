# Deep Explore Windows 全项目启动教程（cmd.exe）

## 这是什么终端？

你截图中的窗口是 Windows Terminal 里的 **命令提示符（Command Prompt，cmd.exe）**，不是 PowerShell。

判断方法：

- 当前提示符：`D:\deep_explore_harness>`
- PowerShell 通常显示：`PS D:\deep_explore_harness>`

下面的命令全部适用于你截图中的 `cmd.exe`。

## 一次性确认前置环境

确保 Docker Desktop 已启动，并处于 Linux 容器模式。项目要求：

- Java 21
- Node.js 22
- Docker Desktop
- 项目根目录已有 `.env`

在 cmd 中执行：

```cmd
cd /d D:\deep_explore_harness
java -version
node --version
npm --version
docker version
docker-compose version
```

`docker version` 必须同时显示 `Client` 和 `Server`。如果只显示 Client，先启动 Docker Desktop，等待 Docker 完全就绪。

## 第一步：启动 PostgreSQL

在一个 cmd 窗口中执行：

```cmd
cd /d D:\deep_explore_harness
set DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine
docker-compose up -d postgres
docker-compose ps
```

看到 PostgreSQL 状态为 `healthy` 后继续。第一次启动可能需要等待几秒。

如果沙箱镜像还不存在，只需首次执行一次：

```cmd
cd /d D:\deep_explore_harness
set DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine
docker build -t deep-explore/sandbox-fullstack:v1 sandbox\java21
```

## 第二步：启动后端

建议新开一个 cmd 标签页，执行：

```cmd
cd /d D:\deep_explore_harness
```

项目的 `.env` 不会被 cmd 或 Spring Boot 自动加载。先在当前窗口执行下面这条命令，把 `.env` 的配置加载进当前 cmd 进程：

```cmd
for /f "usebackq eol=# tokens=1,* delims==" %A in (".env") do @set "%A=%B"
```

然后必须进入后端目录，再启动 Maven：

```cmd
cd deep_explore
mvnw.cmd spring-boot:run
```

注意：这里使用 `mvnw.cmd`，不要使用 `./mvnw`。`./mvnw` 是 Bash 写法，在 Windows 的 cmd 中容易出现 `classworlds.launcher ClassNotFound`。

启动成功的标志是日志出现：

```text
Started DeepExploreApplication
```

不要关闭这个窗口。后端默认监听 `8080` 端口。

## 第三步：启动前端

再新开一个 cmd 标签页，执行：

```cmd
cd /d D:\deep_explore_harness\deep_explore_frontend
```

如果这是第一次启动，先安装依赖：

```cmd
npm install
```

然后启动 Vite：

```cmd
node node_modules\vite\bin\vite.js --clearScreen false
```

看到类似下面的提示即成功：

```text
Local: http://localhost:5173/
```

浏览器打开：

```text
http://localhost:5173/chat
```

请优先使用 `localhost`，不要使用 `127.0.0.1`。

## 第四步：启动工作区沙箱

后端启动后，在任意 cmd 窗口执行：

```cmd
curl.exe --noproxy "*" http://localhost:8080/api/workspaces
```

找到要启动的工作区 `id`，然后设置变量。下面的 ID 只是示例，必须替换成实际 ID：

```cmd
set WORKSPACE_ID=把实际工作区ID填在这里
curl.exe --noproxy "*" -X POST http://localhost:8080/api/workspaces/%WORKSPACE_ID%/start
```

不要把 `把实际工作区ID填在这里` 原样执行。

验证容器是否运行：

```cmd
set DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine
docker ps --filter label=deep-explore.managed=true
```

看到名称类似 `deep-explore-sandbox-...` 且状态为 `Up` 即成功。

## 启动完成后的检查

### 检查后端

```cmd
curl.exe --noproxy "*" http://localhost:8080/api/health
```

预期结果：

```json
{"status":"ok"}
```

### 检查 Docker 运行时

```cmd
curl.exe --noproxy "*" http://localhost:8080/api/runtime-profiles
```

预期包含：

```text
"available":true
"message":"Docker is available"
```

### 检查前端

浏览器打开：

```text
http://localhost:5173/chat
```

## 推荐的窗口安排

建议使用三个 cmd 标签页：

1. 数据库和 Docker 检查
2. 后端：`mvnw.cmd spring-boot:run`
3. 前端：`node node_modules\vite\bin\vite.js --clearScreen false`

后端和前端窗口都不要关闭；关闭窗口会停止对应服务。

## 常见报错处理

### `classworlds.launcher ClassNotFound`

确认你在 cmd 中运行的是：

```cmd
mvnw.cmd spring-boot:run
```

并且当前目录是：

```text
D:\deep_explore_harness\deep_explore
```

### Docker `Access is denied` 或连接到 `docker_engine`

先执行：

```cmd
set DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine
docker version
```

同时确认 Docker Desktop 已启动且使用 Linux 容器模式。

### 后端连接不上 PostgreSQL

执行：

```cmd
cd /d D:\deep_explore_harness
set DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine
docker-compose ps
```

PostgreSQL 必须是 `healthy`。如果不是：

```cmd
docker-compose logs postgres
```

### 端口已被占用

```cmd
netstat -ano | findstr ":5432 :8080 :5173"
```

如果是之前启动的同一个项目进程，回到对应窗口按 `Ctrl+C`。不要随意结束不确定归属的进程。

### `curl` 返回 502 或代理错误

本机代理可能拦截了 localhost 请求。所有本地 curl 都加上：

```cmd
--noproxy "*"
```

例如：

```cmd
curl.exe --noproxy "*" http://localhost:8080/api/health
```

## 停止项目

后端窗口按：

```text
Ctrl+C
```

前端窗口按：

```text
Ctrl+C
```

停止工作区沙箱：

```cmd
set WORKSPACE_ID=实际工作区ID
curl.exe --noproxy "*" -X POST http://localhost:8080/api/workspaces/%WORKSPACE_ID%/stop
```

停止 PostgreSQL：

```cmd
cd /d D:\deep_explore_harness
set DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine
docker-compose stop postgres
```

停止容器不会删除 `.deep-explore-data` 中的工作区代码。

