# Afterbook API Contract (Auth + Subscription)

This app now expects API-based authentication and subscription state before enabling auto-accept.

## Base URL
- Compile-time config via `BuildConfig.API_BASE_URL`.
- Default placeholder in this repo: `https://api.afterbook.example/v1/`.

## Endpoints

### `POST /auth/register`
Request:
```json
{
  "name": "Driver Name",
  "email": "driver@example.com",
  "password": "secret"
}
```

### `POST /auth/login`
Request:
```json
{
  "email": "driver@example.com",
  "password": "secret"
}
```

### `GET /subscription/status`
Headers:
- `Authorization: Bearer <token>`

### `POST /subscription/verify-payment`
Headers:
- `Authorization: Bearer <token>`
Request:
```json
{
  "transactionId": "UPI_TXN_123",
  "provider": "upi"
}
```

## Success response contract
```json
{
  "userId": "user_123",
  "token": "jwt_or_session_token",
  "subscription": {
    "active": true,
    "expiresAt": "2026-12-31T23:59:59Z"
  }
}
```

## Behavior
- If `subscription.active` is `false`, app disables auto-accept and blocks enabling service.
- Service runtime checks `auth_token` and `subscription_active` before automation.
- Subscription expiry (`expiresAt`) is enforced in-app; expired subscriptions automatically disable service and background scanning.
