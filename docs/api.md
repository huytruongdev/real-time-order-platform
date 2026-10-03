# API Design

API design ban đầu chỉ định nghĩa các endpoint chính.

Request/Response schema chi tiết sẽ được thiết kế trong quá trình implementation.

Tất cả ID trong path và body là UUID.

Error response: ProblemDetail (RFC 7807), có thêm property `code` (mã lỗi ổn định) và `errors` (lỗi validation theo field).

Pagination (ADR-035): query `?page=0&size=20` (`page >= 0`, `1 <= size <= 100`). Response:

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
```

Lỗi validation của query/path param: 400 `VALIDATION_FAILED`.

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

Public, không cần đăng nhập. Chỉ trả về restaurant/product ACTIVE. Sort theo `name`, rồi `id`.

GET /api/v1/restaurants

- Paginated. Item: `{ "id", "name", "address" }`.

GET /api/v1/restaurants/{id}

- 404 `RESTAURANT_NOT_FOUND` nếu không tồn tại hoặc INACTIVE.

GET /api/v1/restaurants/{id}/products

- Paginated. Item: `{ "id", "restaurantId", "name", "description", "price", "availableStock" }`.
- 404 `RESTAURANT_NOT_FOUND` nếu restaurant không tồn tại hoặc INACTIVE.

---

## Admin

Tất cả endpoint `/api/v1/admin/**` yêu cầu role ADMIN (401 nếu chưa đăng nhập, 403 nếu sai role).

ADMIN đầu tiên được tạo lúc khởi động từ `ADMIN_EMAIL` / `ADMIN_PASSWORD` (ADR-035).

### Users

POST /api/v1/admin/users

- Body: `{ "email", "password", "name", "role" }`, `role` ∈ `RESTAURANT | DRIVER`.
- 201: giống response của register. 409 `EMAIL_ALREADY_USED`. 400 `VALIDATION_FAILED`.

### Restaurants

Response admin: `{ "id", "ownerUserId", "name", "address", "status", "version", "createdAt", "updatedAt" }`.

POST /api/v1/admin/restaurants

- Body: `{ "ownerUserId", "name", "address" }`. Restaurant mới luôn ACTIVE.
- 201. 400 `INVALID_RESTAURANT_OWNER` nếu owner không tồn tại hoặc không có role RESTAURANT.

GET /api/v1/admin/restaurants (paginated, gồm cả INACTIVE)

GET /api/v1/admin/restaurants/{id}

PUT /api/v1/admin/restaurants/{id}

- Body: `{ "name", "address", "status", "version" }`. `version` là giá trị đã đọc được.
- 409 `CONCURRENT_MODIFICATION` nếu version đã thay đổi.

### Products

Response admin: `{ "id", "restaurantId", "name", "description", "price", "availableStock", "reservedStock", "status", "version", "createdAt", "updatedAt" }`.

POST /api/v1/admin/restaurants/{id}/products

- Body: `{ "name", "description", "price", "initialStock" }`. `price` > 0, tối đa 2 chữ số thập phân. `0 <= initialStock <= 1000000`.
- 201. 404 `RESTAURANT_NOT_FOUND`.

GET /api/v1/admin/restaurants/{id}/products (paginated, gồm cả INACTIVE)

GET /api/v1/admin/products/{id}

PUT /api/v1/admin/products/{id}

- Body: `{ "name", "description", "price", "status", "version" }`. Không thay đổi stock.
- 409 `CONCURRENT_MODIFICATION` nếu version đã thay đổi.

POST /api/v1/admin/products/{id}/stock-adjustments

- Body: `{ "delta" }`, delta khác 0, trong khoảng ±1000000. Cộng atomic vào `availableStock`.
- 200: product sau khi điều chỉnh. 409 `INSUFFICIENT_STOCK` nếu kết quả âm. 400 `INVALID_STOCK_ADJUSTMENT` nếu delta = 0.

---

## Orders (Customer)

Chỉ role CUSTOMER. Order của customer khác → 404 `ORDER_NOT_FOUND`.

Order response:

```json
{
  "id", "customerId", "restaurantId", "status", "cancelReason", "totalAmount",
  "items": [{ "productId", "productName", "unitPrice", "quantity", "subtotal" }],
  "createdAt", "updatedAt"
}
```

POST /api/v1/orders

- Body: `{ "restaurantId", "items": [{ "productId", "quantity" }] }`. 1–50 item, quantity 1–100. Client không gửi giá.
- 201: order CREATED, stock đã được reserve.
- 409 `INSUFFICIENT_STOCK`, 400 `PRODUCT_NOT_AVAILABLE`, 400 `DUPLICATE_ORDER_ITEM`, 404 `RESTAURANT_NOT_FOUND`.

GET /api/v1/orders/{id}

GET /api/v1/orders (paginated, mới nhất trước)

POST /api/v1/orders/{id}/cancel

- 200: order CANCELLED (`cancelReason = CUSTOMER_CANCELLED`), reservation được release.
- 409 `INVALID_ORDER_TRANSITION` nếu không ở CREATED/CONFIRMED. 409 `CONCURRENT_MODIFICATION` nếu đồng thời với thao tác khác.

Business rules:

- Tạo order sẽ reserve inventory.
- Customer chỉ được cancel khi Order ở CREATED hoặc CONFIRMED.
- Cancel hợp lệ khi đã có payment SUCCESS: Payment → REFUNDED (phase Payment).

`GET /api/v1/orders/{id}` là endpoint client dùng để lấy state mới nhất khi WebSocket reconnect.

---

## Orders (Restaurant)

Restaurant cần các thao tác:

- Xem đơn hàng mới
- Confirm order: CREATED → CONFIRMED (chỉ khi Order = CREATED)
- Reject order: CREATED → CANCELLED (chỉ khi Order = CREATED; không có refund vì chưa có payment)
- Chuyển CONFIRMED → PREPARING (chỉ khi payment SUCCESS)
- Chuyển PREPARING → READY (trigger tạo Delivery)

Chỉ role RESTAURANT, chỉ với order của restaurant mình sở hữu (khác → 404 `ORDER_NOT_FOUND`). Response giống Order response.

GET /api/v1/restaurant/orders?status=CREATED (paginated, `status` tuỳ chọn)

POST /api/v1/restaurant/orders/{id}/confirm

- CREATED → CONFIRMED. Reservation được giữ. 409 `INVALID_ORDER_TRANSITION`.

POST /api/v1/restaurant/orders/{id}/reject

- CREATED → CANCELLED (`cancelReason = RESTAURANT_REJECTED`), reservation được release (ADR-036). 409 `INVALID_ORDER_TRANSITION`.

Endpoint PREPARING / READY: phase Payment / Delivery.

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

## Admin (các phase sau)

[NEEDS DESIGN] Endpoint xem order, payment, system events.

---

## WebSocket

WebSocket endpoint và destinations được định nghĩa trong docs/websocket.md.
