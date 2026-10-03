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
- Hủy đơn hàng khi điều kiện cho phép
- Thanh toán đơn hàng
- Theo dõi trạng thái đơn hàng
- Nhận cập nhật đơn hàng theo thời gian thực

### Restaurant

Restaurant có thể:

- Xem đơn hàng mới
- Xác nhận đơn hàng
- Từ chối đơn hàng
- Cập nhật trạng thái chuẩn bị món

### Driver

Driver có thể:

- Xem đơn giao hàng được phân công
- Nhận đơn giao hàng
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

- CANCELLED
- FAILED

Hệ thống phải từ chối các trạng thái chuyển đổi không hợp lệ.

---

## 4. Thanh toán

Customer có thể thanh toán cho đơn hàng.

Các trạng thái:

- PENDING
- SUCCESS
- FAILED
- REFUNDED

Thao tác thanh toán phải có tính idempotent.

Hệ thống phải ngăn việc xử lý thanh toán nhiều lần khi cùng một request được gửi nhiều lần.

---

## 5. Real-time Update

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

---

## 6. Kafka Events

Các domain event chính:

- OrderCreated
- OrderConfirmed
- OrderCancelled
- PaymentCompleted
- PaymentFailed
- DriverAssigned
- OrderDelivered

Kafka được sử dụng cho giao tiếp bất đồng bộ giữa các module có tính liên kết lỏng.

---

## 7. Redis

Redis có thể được sử dụng cho:

- Cache
- Idempotency
- Distributed Lock
- Temporary State

Redis không được sử dụng làm source of truth cho business data.

---

## 8. Concurrency

Hệ thống phải xem xét các request xảy ra đồng thời.

Các trường hợp cần nghiên cứu:

- Hai user cùng mua sản phẩm có số lượng giới hạn
- Một payment request được gửi nhiều lần
- Nhiều request cập nhật cùng một order
- Một Kafka event được xử lý nhiều lần

Các race condition quan trọng phải được xác định và xử lý một cách rõ ràng.

---

## 9. Non-functional Requirements

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