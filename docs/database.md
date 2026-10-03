# Thiết kế Database

## 1. Database

Sử dụng PostgreSQL.

PostgreSQL là source of truth của hệ thống.

Business-critical state không được chỉ lưu ở Redis hoặc Kafka.

Schema được quản lý bằng Flyway migration.

---

## 2. Quy ước chung

### ID

- Tất cả primary key sử dụng UUIDv7.
- UUID được generate ở application layer, không dùng default của database.

UUIDv7 sinh bằng thư viện JUG (ADR-034).

### Kiểu dữ liệu

- Tiền: `NUMERIC(12,2)`, Java `BigDecimal`. Một currency duy nhất (VND), không có cột currency (ADR-035).
- Timestamp: `TIMESTAMPTZ`, Java `Instant` (ADR-034).

### Ownership theo module

Mỗi bảng thuộc về một module. Module khác không truy cập trực tiếp bảng đó qua Repository/Entity.

| Bảng | Module |
|---|---|
| users, refresh_tokens | user |
| restaurants, products (bao gồm inventory) | catalog |
| orders, order_items, order_status_history | order |
| payments | payment |
| deliveries, (driver availability) | delivery |
| processed_events | consumer của từng module (Phase 6) |
| outbox_events | module publish event (Phase 9) |

---

## 3. Các bảng chính

Các field dưới đây là dự kiến. Chi tiết sẽ được chốt khi viết migration của từng phase.

### users

- id (UUID)
- email
- password_hash
- name
- role
- created_at
- updated_at

### refresh_tokens

Refresh token được lưu server-side. Không lưu raw token, chỉ lưu hash.

Hỗ trợ rotation, revoke, expiration.

Các field dự kiến:

- id (UUID)
- user_id
- family_id (các token sinh từ cùng một lần login)
- token_hash (SHA-256 hex)
- expires_at
- revoked_at
- replaced_by (id của token kế tiếp sau rotation)
- created_at

Migration: `V2__create_refresh_tokens.sql`. Chi tiết: ADR-034.

### restaurants

Migration: `V3__create_restaurants.sql`.

- id (UUID)
- owner_user_id (user role RESTAURANT; không có FK sang users)
- name
- address
- status (ACTIVE, INACTIVE)
- version
- created_at
- updated_at

Ownership: `owner_user_id`, một Restaurant user có thể sở hữu nhiều restaurant (ADR-035).

### products

- id (UUID)
- restaurant_id
- name
- description
- price
- available_stock
- reserved_stock
- status
- version (optimistic locking cho thông tin product, KHÔNG tăng khi stock thay đổi)
- created_at
- updated_at

Inventory thuộc Catalog Module.

Ghi chú Phase 3: có thể tạm dùng một cột stock (decrement/restore), nhưng thiết kế phải mở rộng được sang available_stock/reserved_stock.

Migration: `V4__create_products.sql`. Inventory lưu trực tiếp trong bảng products; stock chỉ thay đổi bằng atomic conditional UPDATE (ADR-035).

[NEEDS DESIGN] Cách ghi nhận sold quantity khi reservation được commit (Payment SUCCESS).

[NEEDS DESIGN] Có cần lưu reservation theo từng order (để release/commit chính xác và idempotent) hay không.

### orders

- id (UUID)
- user_id
- restaurant_id
- status (CREATED, CONFIRMED, PREPARING, READY, DRIVER_ASSIGNED, PICKING_UP, DELIVERING, DELIVERED, CANCELLED)
- total_amount
- version
- created_at
- updated_at

Order không có status FAILED, PAID hoặc PAYMENT_EXPIRED.

[NEEDS DESIGN] Lưu lý do cancel (customer cancel, restaurant reject, payment expiration) hay chỉ dựa vào order_status_history.

[NEEDS DESIGN] Lưu thời điểm CONFIRMED / payment deadline ở đâu để phục vụ payment expiration.

### order_items

- id (UUID)
- order_id
- product_id
- quantity
- unit_price
- subtotal

[NEEDS DESIGN] Có snapshot product name tại thời điểm đặt hàng hay không.

### order_status_history

- id (UUID)
- order_id
- old_status
- new_status
- changed_by
- created_at

[NEEDS DESIGN] Giá trị changed_by khi system thay đổi trạng thái (payment expiration, ...).

### payments

- id (UUID)
- order_id
- idempotency_key
- amount
- status (PENDING, SUCCESS, FAILED, REFUNDED, EXPIRED)
- transaction_reference
- created_at
- updated_at

[NEEDS DESIGN] Retry payment: mỗi attempt một record hay cập nhật cùng record.

[NEEDS DESIGN] Cách biểu diễn EXPIRED khi Customer chưa tạo payment attempt nào.

### deliveries

- id (UUID)
- order_id
- driver_id (nullable khi WAITING_FOR_DRIVER)
- status (WAITING_FOR_DRIVER, DRIVER_ASSIGNED, DRIVER_ACCEPTED, PICKING_UP, DELIVERING, DELIVERED)
- assigned_at
- completed_at

Delivery được tạo khi Order chuyển sang READY.

Status của Delivery độc lập với status của Order. Lưu ý: Delivery `DRIVER_ASSIGNED` (đang chờ driver accept) khác với Order `DRIVER_ASSIGNED` (driver đã accept).

Khi driver reject hoặc timeout, Delivery quay lại WAITING_FOR_DRIVER; Order không thay đổi.

[NEEDS DESIGN] driver_id được xóa hay giữ lại khi Delivery quay về WAITING_FOR_DRIVER.

[NEEDS DESIGN] Field phục vụ driver accept timeout (ví dụ accepted_at, assignment deadline).

[NEEDS DESIGN] Lịch sử assign/reject (driver nào đã được assign trước đó) để tránh assign lại cùng driver.

[NEEDS DESIGN] Constraint đảm bảo mỗi Order có tối đa một Delivery.

### Driver availability

[NEEDS DESIGN] Bảng/field lưu driver availability phục vụ auto-assign.

### processed_events (Phase 6)

Dùng cho consumer idempotency.

[NEEDS DESIGN] Các field (ví dụ event_id, consumer, processed_at) và unique key.

### outbox_events (Phase 9)

Chỉ tạo migration ở Phase 9.

- id (UUID)
- aggregate_type
- aggregate_id
- event_type
- payload
- status
- created_at
- published_at

---

## 4. Database Constraints

Database nên enforce các invariant quan trọng khi phù hợp.

Ví dụ:

- Email user phải unique
- Payment idempotency key phải unique
- Refresh token hash phải unique
- Foreign key phải hợp lệ trong cùng module
- Product price phải lớn hơn 0
- Order quantity phải lớn hơn 0
- available_stock >= 0
- reserved_stock >= 0

[NEEDS DESIGN] Constraint đảm bảo một Order không có nhiều hơn một payment SUCCESS.

Foreign key chỉ dùng trong cùng module, không dùng giữa bảng của các module khác nhau (ADR-035).

---

## 5. Concurrency và Locking

Inventory chống oversell bằng database (source of truth), không dùng Redis Lock làm cơ chế chính.

[NEEDS DESIGN] Chọn cơ chế cho inventory: atomic conditional UPDATE, pessimistic lock (SELECT ... FOR UPDATE), hoặc optimistic locking.

Các entity có khả năng xảy ra concurrent update cần xem xét sử dụng Optimistic Locking.

Ví dụ:

```java
@Version
private Long version;
```

Các race condition cần xử lý:

- Nhiều customer cùng reserve một product
- Customer cancel và Restaurant confirm/reject cùng lúc
- Payment SUCCESS và payment expiration cùng lúc
- Driver accept và driver accept timeout cùng lúc

---

## 6. Transaction

Các business operation yêu cầu atomicity phải sử dụng database transaction.

Không giả định rằng database transaction có thể rollback Kafka message.

Transaction giữa database và Kafka phải được thiết kế riêng (Outbox Pattern ở Phase 9).
