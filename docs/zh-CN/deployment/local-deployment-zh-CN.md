# 本地部署

## 一键启动完整本地栈

```powershell
infra\scripts\start-all.ps1
```

该一键脚本会启动 Docker 基础设施，以及 API、AI Worker、Collector 和 Web 应用后台进程。日志和 PID 文件写入 `logs/local/`。

常用参数：

- `-SkipInfrastructure`：不启动 Docker；适用于 MySQL、Redis 和 Qdrant 已经运行的场景。
- `-SkipDependencyInstall`：如果 Python 虚拟环境或 `node_modules` 缺失则快速失败。
- `-OpenBrowser`：请求启动后打开 `http://localhost:3000`。
## 启动基础设施

```powershell
infra\scripts\start-local.ps1
```

该脚本启动：

- MySQL：`localhost:13306`
- Redis：`localhost:16379`
- Qdrant：`http://localhost:16333`

## 健康检查

```powershell
infra\scripts\health-check.ps1
```

## 启动应用服务

基础设施启动后，请在不同的 PowerShell 终端中分别启动各服务。

### API 服务

```powershell
cd services\api
mvn spring-boot:run
```

API 默认监听 `http://localhost:8080`。如需调整端口，可设置 `API_PORT`
环境变量。

### AI Worker

```powershell
cd services\ai-worker
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8100
```

AI Worker 监听 `http://localhost:8100`。

### Collector

```powershell
cd services\collector
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8200
```

Collector 监听 `http://localhost:8200`。

### Web 应用

```powershell
cd apps\web
npm install
npm run dev
```

Web 应用监听 `http://localhost:3000`，并且只能直接调用 API 服务。

## 默认账号

V1 基线迁移会创建三个开发账号。它们使用相同的本地测试密码哈希：

- `admin`
- `operator`
- `user`

应用级认证会在 API 模块中定义本地可接受的实际密码。

