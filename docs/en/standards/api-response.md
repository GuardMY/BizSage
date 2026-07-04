# API Response Convention

All public API responses use the same envelope:

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "requestId": "01J..."
}
```

## Rules

- `code` is stable and machine-readable.
- `message` is human-readable.
- `data` is `null`, an object, or an array.
- `requestId` is generated per request and included in logs.
- Authentication and authorization failures use the same envelope.
