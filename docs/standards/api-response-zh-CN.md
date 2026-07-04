# API 响应规范

所有公开 API 响应使用相同 envelope：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "requestId": "01J..."
}
```

## 规则

- `code` 稳定且可被机器读取。
- `message` 面向人类可读。
- `data` 可以是 `null`、对象或数组。
- `requestId` 每次请求生成，并写入日志。
- 认证和授权失败也使用同一响应结构。
