# Kiến trúc hệ thống

## 1. Kiến trúc ban đầu

Project bắt đầu với kiến trúc Modular Monolith.

Không bắt đầu bằng Microservices.

Mục tiêu là hiểu rõ boundary giữa các business module trước khi tách thành các service độc lập.

---

## 2. High-level Architecture

```text
                    Client
                      |
              REST / WebSocket
                      |
                      v
              Spring Boot App
                      |
        +-------------+-------------+
        |             |             |
        v             v             v
      User          Order        Payment
      Module        Module        Module
                      |
                      v
                  PostgreSQL
                      |
          +-----------+-----------+
          |                       |
          v                       v
        Redis                   Kafka
                                  |
                   +--------------+--------------+
                   |              |              |
                   v              v              v
             Notification     Delivery       Analytics
```

---

## 3. Các module

### User Module

Chịu trách nhiệm:

- Đăng ký
- Đăng nhập
- Authentication
- Authorization
- User profile

### Order Module

Chịu trách nhiệm:

- Tạo order
- Order lifecycle
- Order status
- Order history

### Payment Module

Chịu trách nhiệm:

- Tạo payment
- Xử lý payment
- Payment status
- Idempotency

### Delivery Module

Chịu trách nhiệm:

- Phân công driver
- Delivery lifecycle
- Driver status

### Notification Module

Chịu trách nhiệm:

- Consume events
- Notification
- Publish WebSocket updates

---

## 4. Communication

### Synchronous Communication

Sử dụng REST hoặc internal method call khi cần:

- Response ngay lập tức
- Strong consistency
- Kết quả của operation cần thiết ngay

### Asynchronous Communication

Sử dụng Kafka khi:

- Operation có thể xử lý bất đồng bộ
- Cần giảm coupling giữa các module
- Một event cần nhiều consumer xử lý
- Không cần block request hiện tại để chờ xử lý

---

## 5. Database

PostgreSQL là source of truth.

Các business-critical data phải được lưu trữ trong PostgreSQL.

Redis không thay thế PostgreSQL.

---

## 6. WebSocket

WebSocket được sử dụng để gửi real-time update đến client.

Ví dụ:

```text
Order status changed
        |
        v
Order Module
        |
        v
Kafka
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

## 7. Future Evolution

Sau khi hiểu rõ boundary giữa các module, kiến trúc có thể được tách thành:

- Order Service
- Payment Service
- Delivery Service
- Notification Service

Không tách Microservices chỉ vì muốn sử dụng Microservices.

Việc tách service phải xuất phát từ nhu cầu kỹ thuật hoặc business thực tế.