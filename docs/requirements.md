# Yêu cầu hệ thống

## 1. Mục tiêu dự án

Xây dựng một nền tảng đặt hàng và thanh toán theo thời gian thực nhằm học và nghiên cứu Java Backend theo hướng production-oriented.

Các mục tiêu chính:

- Java 21
- Spring Boot 3
- REST API
- PostgreSQL
- Redis
- Apache Kafka
- WebSocket
- Spring Security
- Event-driven architecture
- Transaction
- Concurrency
- Idempotency
- Distributed system
- Testing
- Observability

Hệ thống cần đủ thực tế để mô phỏng các vấn đề thường gặp trong một backend production.

Các quyết định đã chốt: xem `docs/decisions.md`.

---

## 2. Các actor

### Customer

Customer có thể:

- Đăng ký tài khoản
- Đăng nhập
- Xem danh sách nhà hàng
- Xem sản phẩm
- Tạo đơn hàng
- Xem đơn hàng
- Hủy đơn hàng khi điều kiện cho phép (xem mục 3.3)
- Thanh toán đơn hàng sau khi Restaurant đã confirm
- Retry thanh toán khi payment FAILED
- Theo dõi trạng thái đơn hàng
- Nhận cập nhật đơn hàng theo thời gian thực

### Restaurant

Restaurant có thể:

- Xem đơn hàng mới
- Xác nhận đơn hàng (chỉ khi Order = CREATED)
- Từ chối đơn hàng (chỉ khi Order = CREATED)
- Cập nhật trạng thái chuẩn bị món

Ownership: `restaurants.owner_user_id` (ADR-035).

### Driver

Driver có thể:

- Xem đơn giao hàng được system phân công
- Accept đơn giao hàng được phân công
- Reject đơn giao hàng được phân công
- Cập nhật trạng thái giao hàng
- Hoàn thành giao hàng

### Admin

Admin có thể:

- Quản lý người dùng
- Quản lý nhà hàng
- Quản lý sản phẩm
- Xem đơn hàng
- Xem thanh toán
- Xem system events

---

## 3. Vòng đời đơn hàng

### 3.1 Order states

Vòng đời chính:

CREATED
→ CONFIRMED
→ PREPARING
→ READY
→ DRIVER_ASSIGNED
→ PICKING_UP
→ DELIVERING
→ DELIVERED

Các trạng thái kết thúc:

- DELIVERED
- CANCELLED

Order KHÔNG có trạng thái PAID, PAYMENT_EXPIRED hoặc FAILED.

FAILED không được sử dụng cho Order ở phiên bản hiện tại. FAILED là state của Payment hoặc technical processing. Order dùng CANCELLED cho business cancellation.

Order state machine là monotonic: không có transition đi lùi.

Hệ thống phải từ chối các trạng thái chuyển đổi không hợp lệ.

### 3.2 Order transitions

| From | To | Actor / Trigger | Điều kiện |
|---|---|---|---|
| CREATED | CONFIRMED | Restaurant confirm | |
| CREATED | CANCELLED | Customer cancel | |
| CREATED | CANCELLED | Restaurant reject | Chưa có payment nên không có refund |
| CONFIRMED | PREPARING | Restaurant | Payment phải SUCCESS |
| CONFIRMED | CANCELLED | Customer cancel | Nếu payment SUCCESS: Payment → REFUNDED |
| CONFIRMED | CANCELLED | System (payment expiration) | Chưa có payment SUCCESS |
| PREPARING | READY | Restaurant | Trigger tạo Delivery |
| READY | DRIVER_ASSIGNED | System, khi Delivery → DRIVER_ACCEPTED | Driver đã accept |
| DRIVER_ASSIGNED | PICKING_UP | Theo Delivery → PICKING_UP | |
| PICKING_UP | DELIVERING | Theo Delivery → DELIVERING | |
| DELIVERING | DELIVERED | Theo Delivery → DELIVERED | |

Mọi transition khác đều không hợp lệ, bao gồm:

- DRIVER_ASSIGNED → READY
- Bất kỳ transition nào từ DELIVERED hoặc CANCELLED

### 3.3 Quan hệ với Payment

Order và Payment là hai state machine độc lập.

Payment chỉ được thực hiện sau khi Order ở trạng thái CONFIRMED.

Payment phải SUCCESS trước khi Order được chuyển sang PREPARING.

```text
Order CONFIRMED
    ↓
Payment PENDING
    ↓
Payment SUCCESS
    ↓
Order PREPARING
```

### 3.4 Quan hệ với Delivery

Order chỉ chuyển sang DRIVER_ASSIGNED khi driver đã ACCEPT.

Trong suốt quá trình Delivery assign/retry (WAITING_FOR_DRIVER ↔ DRIVER_ASSIGNED), Order vẫn ở READY.

Lưu ý tên trạng thái:

- Order `DRIVER_ASSIGNED` = driver đã accept.
- Delivery `DRIVER_ASSIGNED` = system đã chọn driver, đang chờ driver accept.

| Delivery transition | Order transition |
|---|---|
| (tạo Delivery) → WAITING_FOR_DRIVER | Không đổi (READY) |
| WAITING_FOR_DRIVER → DRIVER_ASSIGNED | Không đổi (READY) |
| DRIVER_ASSIGNED → WAITING_FOR_DRIVER | Không đổi (READY) |
| DRIVER_ASSIGNED → DRIVER_ACCEPTED | READY → DRIVER_ASSIGNED |
| DRIVER_ACCEPTED → PICKING_UP | DRIVER_ASSIGNED → PICKING_UP |
| PICKING_UP → DELIVERING | PICKING_UP → DELIVERING |
| DELIVERING → DELIVERED | DELIVERING → DELIVERED |

Mục tiêu: giữ Order state machine monotonic và không để Order phụ thuộc vào chi tiết retry của Delivery.

[NEEDS DESIGN] Cách Delivery thông báo cho Order (synchronous call ở Phase 5, event ở Phase 6) và đảm bảo hai state được cập nhật nhất quán.

### 3.5 Customer cancel

Customer được phép cancel khi Order ở:

- CREATED
- CONFIRMED

Customer KHÔNG được tự cancel khi Order ở:

- PREPARING
- READY
- DRIVER_ASSIGNED
- PICKING_UP
- DELIVERING

Nếu Order đã có payment SUCCESS và được cancel hợp lệ:

- Payment → REFUNDED
- Inventory reservation/sold quantity được release/return theo inventory policy.

[NEEDS DESIGN] Inventory policy chi tiết cho việc return sold quantity.

[NEEDS DESIGN] Xử lý khi Customer cancel trong lúc đang có payment PENDING hoặc FAILED.

### 3.6 Restaurant reject

Restaurant chỉ được reject khi Order = CREATED.

```text
CREATED → CANCELLED
```

Vì payment chỉ được tạo sau CONFIRMED, Order bị reject chưa có payment SUCCESS nên không có refund.

### 3.7 System cancel

Trường hợp system cancel duy nhất ở phiên bản hiện tại: payment expiration (xem mục 4.2).

Không tự động cancel Order chỉ vì chưa tìm được driver. Nếu không có driver, Delivery ở WAITING_FOR_DRIVER và tiếp tục retry assignment.

### 3.2 Quan hệ với Payment

Order và Payment là hai state machine độc lập.

Payment chỉ được thực hiện sau khi Order ở trạng thái CONFIRMED.

Payment phải SUCCESS trước khi Order được chuyển sang PREPARING.

```text
Order CONFIRMED
    ↓
Payment PENDING
    ↓
Payment SUCCESS
    ↓
Order PREPARING
```

### 3.3 Customer cancel

Customer được phép cancel khi Order ở:

- CREATED
- CONFIRMED

Customer KHÔNG được tự cancel khi Order ở:

- PREPARING
- READY
- DRIVER_ASSIGNED
- PICKING_UP
- DELIVERING

Nếu Order đã có payment SUCCESS và được cancel hợp lệ:

- Payment → REFUNDED
- Inventory reservation/sold quantity được release/return theo inventory policy.

[NEEDS DESIGN] Inventory policy chi tiết cho việc return sold quantity.

[NEEDS DESIGN] Xử lý khi Customer cancel trong lúc đang có payment PENDING.

---

## 4. Thanh toán

### 4.1 Payment states

- PENDING
- SUCCESS
- FAILED
- REFUNDED
- EXPIRED

| From | To | Trigger | Điều kiện |
|---|---|---|---|
| (mới) | PENDING | Customer tạo payment | Order = CONFIRMED |
| PENDING | SUCCESS | Payment xử lý thành công | |
| PENDING | FAILED | Payment xử lý thất bại | |
| FAILED | PENDING | Customer retry | Order = CONFIRMED, chưa hết hạn |
| SUCCESS | REFUNDED | Customer cancel hợp lệ | Order = CONFIRMED |
| FAILED | EXPIRED | Payment expiration | |
| PENDING | EXPIRED | Payment expiration | [NEEDS DESIGN] xem 4.2 |

Trạng thái kết thúc: SUCCESS (trừ khi refund), REFUNDED, EXPIRED.

FAILED KHÔNG phải trạng thái kết thúc: Payment FAILED không làm Order kết thúc, Customer được retry.

Vì Customer chỉ cancel được ở CREATED/CONFIRMED, REFUNDED chỉ xảy ra khi Order đang ở CONFIRMED (payment SUCCESS nhưng Restaurant chưa chuyển sang PREPARING).

[NEEDS DESIGN] Retry tạo payment record mới (mỗi attempt một record) hay chuyển lại trạng thái trên cùng record. Bảng trên mô tả state của một payment theo logic; cách lưu sẽ quyết định ở Payment phase.

Thao tác thanh toán phải có tính idempotent.

Hệ thống phải ngăn việc xử lý thanh toán nhiều lần khi cùng một request được gửi nhiều lần.

### 4.2 Payment expiration

Sau khi Order chuyển sang CONFIRMED, Customer có một khoảng thời gian để thanh toán.

Khoảng thời gian này là configurable application property:

```text
payment.expiration-minutes
```

Giá trị mặc định đề xuất: 15 phút. Đây là configuration, không phải business constant.

Nếu hết thời gian mà chưa có payment SUCCESS:

- Payment → EXPIRED
- Order → CANCELLED
- Inventory reservation được release

[NEEDS DESIGN] Payment EXPIRED áp dụng thế nào khi Customer chưa từng tạo payment attempt nào.

[NEEDS DESIGN] Cơ chế phát hiện expiration (scheduled job, ...).

[NEEDS DESIGN] Xử lý race condition giữa payment SUCCESS và expiration xảy ra đồng thời, và khi payment đang PENDING lúc hết hạn.

---

## 5. Inventory

Catalog Module sở hữu Product và Inventory.

Khi Customer tạo Order, stock được RESERVE:

```text
Ban đầu:          available_stock = 10, reserved_stock = 0
Customer đặt 3:   available_stock = 7,  reserved_stock = 3
```

- Payment SUCCESS: reservation được giữ và chuyển thành sold.
- Payment FAILED: reservation KHÔNG được release.

Reservation chỉ được release khi:

- Customer cancel Order hợp lệ
- Payment timeout/expiration
- Order bị system cancel

[NEEDS DECISION] Restaurant reject (CREATED → CANCELLED) có release reservation hay không. Danh sách trên chưa bao gồm trường hợp này; nếu không release thì stock bị giữ vĩnh viễn cho một Order đã CANCELLED.

Database là source of truth cho inventory.

Không sử dụng Redis Lock làm cơ chế chính để chống oversell.

Phase 3 có thể tạm dùng stock decrement và restore stock khi cancel, nhưng code phải thiết kế để mở rộng sang available/reserved stock.

---

## 6. Delivery

System tự động assign driver khi Order chuyển sang READY.

Delivery flow:

```text
WAITING_FOR_DRIVER
    ↓
DRIVER_ASSIGNED
    ↓
DRIVER_ACCEPTED
    ↓
PICKING_UP
    ↓
DELIVERING
    ↓
DELIVERED
```

| From | To | Trigger | Order |
|---|---|---|---|
| (mới) | WAITING_FOR_DRIVER | Order → READY | READY |
| WAITING_FOR_DRIVER | DRIVER_ASSIGNED | System assign driver | READY |
| DRIVER_ASSIGNED | WAITING_FOR_DRIVER | Driver reject hoặc không accept trong configured timeout | READY |
| DRIVER_ASSIGNED | DRIVER_ACCEPTED | Driver accept | READY → DRIVER_ASSIGNED |
| DRIVER_ACCEPTED | PICKING_UP | Driver | → PICKING_UP |
| PICKING_UP | DELIVERING | Driver | → DELIVERING |
| DELIVERING | DELIVERED | Driver | → DELIVERED |

Trạng thái kết thúc: DELIVERED.

Nếu driver reject hoặc không accept trong configured timeout:

```text
DRIVER_ASSIGNED → WAITING_FOR_DRIVER → assign driver khác
```

Nếu không có driver available: Delivery ở WAITING_FOR_DRIVER và tiếp tục retry assignment. Order KHÔNG bị cancel vì chưa tìm được driver.

Delivery tự xử lý toàn bộ retry assignment; Order không biết về các lần retry.

[NEEDS DESIGN] Tiêu chí chọn driver, cách lưu driver availability, tên property cho driver accept timeout, retry assignment policy khi không có driver.

[NEEDS DESIGN] Transition sau DRIVER_ACCEPTED khi driver không thể tiếp tục giao hàng (hiện chưa có business requirement).

---

## 7. Real-time Update

Customer có thể theo dõi trạng thái đơn hàng theo thời gian thực.

Ví dụ:

```text
PREPARING
    ↓
READY
    ↓
DRIVER_ASSIGNED
```

Khi trạng thái thay đổi, client đang kết nối WebSocket phải nhận được cập nhật mà không cần polling REST API.

Real-time update dựa trên event `OrderStatusChanged`.

Khi reconnect, client lấy state mới nhất bằng REST API.

---

## 8. Kafka Events

Specific domain events:

- OrderCreated
- OrderConfirmed
- OrderCancelled
- PaymentCompleted
- PaymentFailed
- DriverAssigned
- OrderDelivered

Event phục vụ notification/WebSocket:

- OrderStatusChanged

Specific event và OrderStatusChanged có thể được publish cho cùng một state transition.

Kafka được sử dụng cho giao tiếp bất đồng bộ giữa các module có tính liên kết lỏng, bắt đầu từ Phase 6.

Chi tiết: `docs/kafka.md`.

---

## 9. Redis

Redis được sử dụng cho:

- Cache
- Idempotency

Distributed Lock chỉ dùng để nghiên cứu và so sánh với database locking, không phải giải pháp mặc định.

Redis không được sử dụng làm source of truth cho business data.

---

## 10. Concurrency

Hệ thống phải xem xét các request xảy ra đồng thời.

Các trường hợp cần nghiên cứu:

- Hai user cùng mua sản phẩm có số lượng giới hạn
- Một payment request được gửi nhiều lần
- Nhiều request cập nhật cùng một order
- Một Kafka event được xử lý nhiều lần
- Payment SUCCESS và payment expiration xảy ra đồng thời
- Customer cancel và Restaurant confirm xảy ra đồng thời

Các race condition quan trọng phải được xác định và xử lý một cách rõ ràng.

---

## 11. Non-functional Requirements

Hệ thống hướng tới:

- Maintainability
- Clear separation of responsibilities
- Transactional consistency
- Idempotent event processing
- Reasonable performance
- Testability
- Observability

Không tối ưu performance khi chưa xác định được bottleneck.

Ưu tiên đo lường trước khi đưa vào các kỹ thuật tối ưu phức tạp.

[NEEDS DESIGN] Observability (metrics, logging, tracing) chưa có tài liệu riêng.
