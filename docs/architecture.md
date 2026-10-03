# Kiến trúc hệ thống

## 1. Kiến trúc ban đầu

Project sử dụng kiến trúc Modular Monolith.

Không sử dụng Microservices. Không tự ý tách service.

Mục tiêu là hiểu rõ boundary giữa các business module trước khi cân nhắc tách thành các service độc lập.

Các quyết định đã chốt: xem `docs/decisions.md`.

---

## 2. High-level Architecture

Application ghi trực tiếp vào PostgreSQL, Redis và Kafka. Không có CDC từ PostgreSQL.

```text
                         Client
                           |
                   REST / WebSocket
                           |
                           v
+---------------------------------------------------------+
|                 Spring Boot Application                 |
|                                                         |
|  user   catalog   order   payment   delivery   notification
|                                                         |
+---------------------------------------------------------+
        |                  |                    |
        v                  v                    v
   PostgreSQL            Redis                Kafka
 (source of truth)  (cache, idempotency)  (async events, từ Phase 6)
                                                |
                                                v
                                   Consumers trong cùng application
                                   (notification, delivery, ...)
```

Kafka consumer nằm trong cùng application (module consume event do module khác publish).

[NEEDS DESIGN] Analytics consumer: chưa thuộc scope hiện tại, chỉ là hướng mở rộng trong tương lai.

---

## 3. Các module

### User Module

Chịu trách nhiệm:

- Đăng ký
- Đăng nhập
- Authentication (JWT Access Token + Refresh Token)
- Refresh token rotation/revoke/expiration
- Authorization
- User profile

### Catalog Module

Chịu trách nhiệm:

- Restaurant
- Product
- Inventory (available_stock, reserved_stock)

Cung cấp public interface cho inventory, ví dụ:

- reserve(...)
- release(...)
- commit(...)

Chữ ký cụ thể: `InventoryService` (ADR-036).

### Order Module

Chịu trách nhiệm:

- Tạo order
- Order lifecycle (state machine monotonic, không có transition đi lùi)
- Order status
- Order history
- Customer cancel (CREATED, CONFIRMED)
- Restaurant confirm/reject (chỉ khi CREATED)
- System cancel do payment expiration

Order KHÔNG truy cập trực tiếp `ProductRepository` hoặc `Product` entity của Catalog.

Order không biết về chi tiết retry assignment của Delivery. Order chỉ chuyển READY → DRIVER_ASSIGNED khi Delivery báo driver đã ACCEPT.

Order không dùng trạng thái FAILED ở phiên bản hiện tại.

### Payment Module

Chịu trách nhiệm:

- Tạo payment (chỉ khi Order ở CONFIRMED)
- Xử lý payment
- Payment retry sau FAILED
- Payment expiration (EXPIRED)
- Refund (REFUNDED)
- Payment status
- Idempotency

Payment là state machine độc lập với Order.

### Delivery Module

Chịu trách nhiệm:

- Tự động assign driver khi Order READY
- Delivery lifecycle (WAITING_FOR_DRIVER → ... → DELIVERED)
- Re-assign khi driver reject hoặc không accept trong configured timeout
- Retry assignment khi không có driver available (không cancel Order)
- Driver status/availability
- Thông báo cho Order khi driver ACCEPT và khi delivery tiến tới PICKING_UP, DELIVERING, DELIVERED

---

## 3a. State Machines

Order, Payment và Delivery là ba state machine độc lập, mỗi module sở hữu state machine của mình.

Chi tiết transition: `docs/requirements.md` (mục 3, 4, 6).

```text
Order:    CREATED → CONFIRMED → PREPARING → READY → DRIVER_ASSIGNED → PICKING_UP → DELIVERING → DELIVERED
          CREATED → CANCELLED, CONFIRMED → CANCELLED

Payment:  PENDING → SUCCESS → REFUNDED
          PENDING → FAILED → PENDING (retry)
          PENDING/FAILED → EXPIRED

Delivery: WAITING_FOR_DRIVER → DRIVER_ASSIGNED → DRIVER_ACCEPTED → PICKING_UP → DELIVERING → DELIVERED
          DRIVER_ASSIGNED → WAITING_FOR_DRIVER (reject/timeout)
```

Điểm liên kết giữa các state machine:

| Sự kiện | Ảnh hưởng |
|---|---|
| Payment SUCCESS | Cho phép Order CONFIRMED → PREPARING |
| Payment expiration | Payment → EXPIRED, Order CONFIRMED → CANCELLED, release reservation |
| Customer cancel khi payment SUCCESS | Order → CANCELLED, Payment → REFUNDED |
| Order → READY | Tạo Delivery ở WAITING_FOR_DRIVER |
| Delivery → DRIVER_ACCEPTED | Order READY → DRIVER_ASSIGNED |
| Delivery → PICKING_UP / DELIVERING / DELIVERED | Order chuyển sang trạng thái cùng tên |

### Notification Module

Chịu trách nhiệm:

- Consume `OrderStatusChanged`
- Notification
- Publish WebSocket updates

---

## 4. Module Boundary

- Một module không truy cập trực tiếp Repository hoặc Entity của module khác.
- Module chỉ gọi nhau qua public application/domain interface.
- Business state của mỗi module do chính module đó sở hữu.

[NEEDS DESIGN] Package structure và cách enforce boundary (convention, ArchUnit, ...).

---

## 5. Communication

### Synchronous Communication

Sử dụng internal method call (qua public interface) khi cần:

- Response ngay lập tức
- Strong consistency
- Kết quả của operation cần thiết ngay

Ví dụ: Order gọi Catalog để reserve inventory khi tạo order.

Phase 5 implement toàn bộ flow theo cách synchronous trước để hiểu coupling.

### Asynchronous Communication

Sử dụng Kafka (từ Phase 6) khi:

- Operation có thể xử lý bất đồng bộ
- Cần giảm coupling giữa các module
- Một event cần nhiều consumer xử lý
- Không cần block request hiện tại để chờ xử lý

[NEEDS DESIGN] Những interaction nào chuyển từ synchronous sang Kafka ở Phase 6.

---

## 6. Database

PostgreSQL là source of truth.

Các business-critical data phải được lưu trữ trong PostgreSQL.

Redis không thay thế PostgreSQL. Kafka không thay thế PostgreSQL.

---

## 7. WebSocket

WebSocket được sử dụng để gửi real-time update đến client.

WebSocket chỉ là transport, không phải source of truth.

Flow (sau khi có Kafka ở Phase 6):

```text
Order status changed
        |
        v
Order Module (OrderStatusChanged)
        |
        v
Kafka (order-events, key = orderId)
        |
        v
Notification Module
        |
        v
WebSocket
        |
        v
Customer
```

---

## 8. Phase Constraints

- Phase 3: chưa dùng Kafka.
- Phase 5: synchronous flow.
- Phase 6: đưa Kafka vào.
- Phase 9: Outbox Pattern.

---

## 9. Future Evolution

Sau khi hiểu rõ boundary giữa các module, kiến trúc có thể được tách thành service độc lập.

Không tách Microservices chỉ vì muốn sử dụng Microservices.

Việc tách service phải xuất phát từ nhu cầu kỹ thuật hoặc business thực tế và phải được quyết định rõ ràng (hiện tại: không tách).
