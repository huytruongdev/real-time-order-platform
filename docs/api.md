# API Design

API design ban đầu chỉ định nghĩa các endpoint chính.

Request/Response schema chi tiết sẽ được thiết kế trong quá trình implementation.

Tất cả ID trong path và body là UUID.

Error response: ProblemDetail (RFC 7807), có thêm property `code` (mã lỗi ổn định) và `errors` (lỗi validation theo field).

[NEEDS DESIGN] Pagination cho các endpoint dạng list.

---

## Authentication

Access token: JWT, stateless.

Refresh token: lưu server-side (hash) trong PostgreSQL, hỗ trợ rotation, revoke, expiration.

POST /api/v1/auth/register

- Body: `{ "email", "password", "name" }`. Luôn tạo role CUSTOMER.
- 201: `{ "id", "email", "name", "role", "createdAt" }`. 409 `EMAIL_ALREADY_USED`. 400 `VALIDATION_FAILED`.

POST /api/v1/auth/login

- Body: `{ "email", "password" }`.
- 200: `{ "tokenType": "Bearer", "accessToken", "accessTokenExpiresAt", "refreshToken", "refreshTokenExpiresAt" }`.
- 401 `INVALID_CREDENTIALS` (dùng chung cho sai email và sai password).

POST /api/v1/auth/refresh

- Body: `{ "refreshToken" }`. Rotation: trả về cặp token mới, token cũ bị revoke.
- 401 `INVALID_REFRESH_TOKEN` (không tồn tại, hết hạn, đã revoke, hoặc reuse).

POST /api/v1/auth/logout

- Body: `{ "refreshToken" }`. Revoke token được gửi lên. 204, idempotent.

---

## Users

GET /api/v1/users/me

- Yêu cầu `Authorization: Bearer <accessToken>`.
- 200: thông tin user hiện tại. 401 nếu thiếu hoặc sai access token.

---

## Catalog (Restaurants / Products)

GET /api/v1/restaurants

GET /api/v1/restaurants/{id}

GET /api/v1/restaurants/{id}/products

[NEEDS DESIGN] Endpoint quản lý restaurant/product/inventory cho Admin.

---

## Orders (Customer)

POST /api/v1/orders

GET /api/v1/orders/{id}

GET /api/v1/orders

POST /api/v1/orders/{id}/cancel

Business rules:

- Tạo order sẽ reserve inventory.
- Customer chỉ được cancel khi Order ở CREATED hoặc CONFIRMED.
- Cancel hợp lệ khi đã có payment SUCCESS: Payment → REFUNDED.

`GET /api/v1/orders/{id}` là endpoint client dùng để lấy state mới nhất khi WebSocket reconnect.

---

## Orders (Restaurant)

Restaurant cần các thao tác:

- Xem đơn hàng mới
- Confirm order: CREATED → CONFIRMED (chỉ khi Order = CREATED)
- Reject order: CREATED → CANCELLED (chỉ khi Order = CREATED; không có refund vì chưa có payment)
- Chuyển CONFIRMED → PREPARING (chỉ khi payment SUCCESS)
- Chuyển PREPARING → READY (trigger tạo Delivery)

[NEEDS DESIGN] Path cụ thể cho các endpoint Restaurant.

---

## Payments

POST /api/v1/orders/{id}/payments

GET /api/v1/orders/{id}/payments

Business rules:

- Chỉ tạo payment khi Order ở CONFIRMED.
- Request tạo payment phải idempotent (idempotency key).
- Payment FAILED cho phép Customer retry cho cùng Order (FAILED → PENDING), miễn là chưa hết hạn.
- Hết `payment.expiration-minutes` sau khi Order CONFIRMED mà chưa SUCCESS: Payment → EXPIRED, Order → CANCELLED, release reservation.
- Payment SUCCESS là điều kiện để Restaurant chuyển Order CONFIRMED → PREPARING.

[NEEDS DESIGN] Cách client gửi idempotency key (ví dụ header `Idempotency-Key`).

[NEEDS DESIGN] Response khi cùng idempotency key nhưng khác payload, và khi request trùng đang được xử lý.

---

## Delivery (Driver)

GET /api/v1/deliveries/{id}

POST /api/v1/deliveries/{id}/accept

POST /api/v1/deliveries/{id}/status

Business rules:

- Driver được system tự động assign khi Order READY.
- Driver accept delivery được assign cho mình: Delivery DRIVER_ASSIGNED → DRIVER_ACCEPTED, đồng thời Order READY → DRIVER_ASSIGNED.
- Driver reject hoặc không accept trong configured timeout: Delivery DRIVER_ASSIGNED → WAITING_FOR_DRIVER và assign driver khác. Order vẫn ở READY.
- Không có driver available: Delivery ở WAITING_FOR_DRIVER, Order vẫn ở READY, không bị cancel.
- Driver cập nhật PICKING_UP → DELIVERING → DELIVERED; Order chuyển sang trạng thái cùng tên.

[NEEDS DESIGN] Endpoint để Driver reject delivery (ví dụ `POST /api/v1/deliveries/{id}/reject`).

[NEEDS DESIGN] Endpoint để Driver xem danh sách delivery được assign cho mình.

[NEEDS DESIGN] `POST /deliveries/{id}/status` chỉ cho phép DRIVER_ACCEPTED → PICKING_UP, PICKING_UP → DELIVERING, DELIVERING → DELIVERED; format request cụ thể.

---

## Admin

[NEEDS DESIGN] Endpoint quản lý user, restaurant, product; xem order, payment, system events.

---

## WebSocket

WebSocket endpoint và destinations được định nghĩa trong docs/websocket.md.
