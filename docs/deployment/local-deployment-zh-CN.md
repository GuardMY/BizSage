# 本地部署

## 启动基础设施

```powershell
infra\scripts\start-local.ps1
```

该脚本启动：

- MySQL：`localhost:3306`
- Redis：`localhost:6379`
- Qdrant：`http://localhost:6333`

## 健康检查

```powershell
infra\scripts\health-check.ps1
```

## 默认账号

V1 基线迁移会创建三个开发账号。它们使用相同的本地测试密码哈希：

- `admin`
- `operator`
- `user`

应用级认证会在 API 模块中定义本地可接受的实际密码。
