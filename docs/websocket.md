# WebSocket Design

## 1. Mục đích

WebSocket cung cấp real-time update cho client.

Use case chính là theo dõi trạng thái order.

WebSocket chỉ là transport, không phải source of truth.

---

## 2. Flow

Real-time update dựa trên event `OrderStatusChanged`.

```text
Order status changed
        |
        v
Order Module (OrderStatusChanged)
        |
        v
Kafka (topic = order-events, key = orderId)
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

WebSocket được implement sau khi có Kafka (Phase 6).

---

## 3. Client Subscription

[NEEDS DESIGN] Có dùng STOMP hay không.

[NEEDS DESIGN] Tách rõ:

- WebSocket handshake endpoint (ví dụ `/ws`)
- Destination để subscribe update của một order (ví dụ theo orderId hoặc theo user)

Giá trị `/ws/orders/{orderId}` trước đây trộn lẫn endpoint và destination, cần được thiết kế lại.

---

## 4. Event Example

```json
{
  "orderId": "0190a1b2-0000-7000-8000-000000000001",
  "status": "PREPARING",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

`orderId` là UUID.

[NEEDS DESIGN] Có thêm `eventId` và/hoặc `version` vào message để client loại bỏ message duplicate hoặc cũ hơn hay không.

---

## 5. Các vấn đề cần xem xét

Implementation cần nghiên cứu:

- Authentication (JWT access token khi kết nối)
- Authorization (Customer chỉ subscribe order của mình)
- Connection lifecycle
- Reconnection
- Multiple devices
- Offline client
- Duplicate message
- Message ordering
- Scalability (nhiều application instance: event phải đến được instance đang giữ connection)

[NEEDS DESIGN] Cách truyền JWT khi kết nối WebSocket.

[NEEDS DESIGN] Consumer group strategy của Notification Module khi chạy nhiều instance.

---

## 6. WebSocket không phải source of truth

Message gửi khi client offline có thể bị mất. Điều này được chấp nhận.

Khi reconnect, client gọi REST API để lấy trạng thái order mới nhất:

```text
GET /api/v1/orders/{id}
```

WebSocket chỉ là phương thức transport cho real-time update.
