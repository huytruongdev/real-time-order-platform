# Testing Strategy

## 0. Công cụ

- JUnit 5
- Mockito
- AssertJ
- Spring Boot Test
- Testcontainers

Integration test phải sử dụng PostgreSQL/Redis/Kafka thật thông qua Testcontainers khi phù hợp.

Testcontainers yêu cầu Docker đang chạy trên máy dev/CI.

---

## 1. Unit Test

Test business logic độc lập.

Ví dụ:

- Order state transition: toàn bộ transition hợp lệ trong `docs/requirements.md` mục 3.2 và các transition không hợp lệ
- Order state machine monotonic: DRIVER_ASSIGNED → READY bị từ chối; không có transition nào từ DELIVERED/CANCELLED
- Order không có trạng thái FAILED
- Payment state transition: PENDING → SUCCESS/FAILED, FAILED → PENDING (retry), SUCCESS → REFUNDED, PENDING/FAILED → EXPIRED
- Delivery state transition: WAITING_FOR_DRIVER → DRIVER_ASSIGNED → DRIVER_ACCEPTED → PICKING_UP → DELIVERING → DELIVERED; DRIVER_ASSIGNED → WAITING_FOR_DRIVER khi reject/timeout
- Rule: Order chỉ được chuyển sang PREPARING khi payment SUCCESS
- Rule: Customer chỉ được cancel khi Order ở CREATED hoặc CONFIRMED
- Rule: Restaurant chỉ được confirm/reject khi Order ở CREATED; reject → CANCELLED, không refund
- Rule: payment chỉ được tạo khi Order ở CONFIRMED
- Price calculation
- Payment idempotency
- Validation

---

## 2. Integration Test

Test integration với:

- PostgreSQL
- Redis
- Kafka

Ưu tiên sử dụng Testcontainers cho infrastructure-dependent tests.

Các scenario chính:

- Flyway migration chạy được trên PostgreSQL thật
- Tạo order reserve inventory (available_stock giảm, reserved_stock tăng)
- Payment SUCCESS commit reservation thành sold
- Payment FAILED KHÔNG release reservation
- Customer cancel release reservation
- Cancel sau payment SUCCESS: Payment → REFUNDED, inventory được return theo inventory policy
- Payment expiration: Payment → EXPIRED, Order → CANCELLED, reservation được release
- Payment FAILED rồi retry thành công: Order vẫn ở CONFIRMED trong suốt quá trình, sau đó được chuyển sang PREPARING
- Order → READY tạo Delivery ở WAITING_FOR_DRIVER
- Driver reject/timeout: Delivery quay lại WAITING_FOR_DRIVER, assign driver khác, Order vẫn ở READY
- Không có driver available: Delivery ở WAITING_FOR_DRIVER, Order vẫn ở READY và KHÔNG bị cancel
- Driver accept: Delivery → DRIVER_ACCEPTED và Order READY → DRIVER_ASSIGNED
- Delivery PICKING_UP/DELIVERING/DELIVERED đồng bộ Order sang trạng thái cùng tên
- Refresh token rotation, revoke, expiration; DB chỉ lưu hash
- Order module không truy cập trực tiếp Repository/Entity của Catalog

---

## 3. Concurrency Test

Các scenario quan trọng:

- Duplicate payment (cùng idempotency key gửi đồng thời)
- Concurrent inventory reserve (không oversell)
- Concurrent order status update (ví dụ Customer cancel và Restaurant confirm/reject cùng lúc)
- Payment SUCCESS và payment expiration xảy ra đồng thời
- Driver accept và driver accept timeout xảy ra đồng thời
- Driver accept và driver reject của cùng delivery gửi đồng thời

---

## 4. Kafka Test

Cần test:

- Event publishing (topic, key = orderId cho order-events)
- Specific event và OrderStatusChanged cho cùng một transition
- Event consumption
- Duplicate event
- Retry
- Dead Letter handling

---

## 5. WebSocket Test

Cần test:

- Authentication
- Authorization (Customer chỉ nhận update của order của mình)
- Subscription
- Order update delivery (OrderStatusChanged)
- Reconnection behavior: sau khi reconnect, client lấy state mới nhất qua REST API

---

## 6. Redis Test

Cần test:

- Cache hit / miss / invalidation
- Idempotency key
- Hệ thống vẫn đúng khi Redis mất dữ liệu (PostgreSQL là source of truth)

Distributed Lock chỉ test trong phạm vi nghiên cứu/so sánh.

---

## 7. Testing Principle

Không viết test chỉ để tăng code coverage.

Test phải bảo vệ:

- Business rules
- Important edge cases
- Failure scenarios
- Concurrency behavior
- Integration behavior
