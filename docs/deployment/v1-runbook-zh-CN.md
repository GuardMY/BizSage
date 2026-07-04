# BizSage V1 运行手册

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
