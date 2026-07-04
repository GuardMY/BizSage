# BizSage V1/V2 运行手册

## 前置依赖

- Java 21
- Maven 3.9+
- Node.js 20+
- Python 3.12+
- Docker Desktop 或兼容 Docker CLI

## 启动基础设施

```powershell
infra\scripts\start-local.ps1
```

当前实现环境中 Docker CLI 不可用，因此 compose 启动需要在已安装 Docker 的机器上验证。

## 启动 API

```powershell
cd services\api
mvn spring-boot:run
```

V2 灰度 profile 启动：

```powershell
cd services\api
$env:BIZSAGE_PROFILE="prod-gray"
mvn spring-boot:run
```

## 启动 Collector

```powershell
cd services\collector
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8200
```

## 启动 AI Worker

```powershell
cd services\ai-worker
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8100
```

## 启动 Web

```powershell
cd apps\web
npm install
npm run dev
```

打开 `http://localhost:3000`。

## V2 备份与恢复演练

在具备 Docker 的预发机器运行：

```powershell
infra\scripts\backup-mysql.ps1
infra\scripts\restore-mysql.ps1 -InputPath <backup.sql>
infra\scripts\backup-qdrant.ps1
infra\scripts\restore-qdrant.ps1 -InputPath <backup.tar>
```

将 RTO、RPO、操作者、时间戳和失败情况记录到 `docs/zh-CN/milestones/v2-verification-results-zh-CN.md`。
