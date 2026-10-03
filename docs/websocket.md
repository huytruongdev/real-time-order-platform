# WebSocket Design

## 1. Mục đích

WebSocket cung cấp real-time update cho client.

Use case chính là theo dõi trạng thái order.

---

## 2. Flow

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

## 3. Client Subscription

Destination dự kiến:

```text
/ws/orders/{orderId}
```

Chi tiết WebSocket và STOMP sẽ được quyết định trong quá trình implementation.

---

## 4. Event Example

```json
{
  "orderId": 123,
  "status": "PREPARING",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

---

## 5. Các vấn đề cần xem xét

Implementation cần nghiên cứu:

- Authentication
- Authorization
- Connection lifecycle
- Reconnection
- Multiple devices
- Offline client
- Duplicate message
- Message ordering
- Scalability

---

## 6. WebSocket không phải source of truth

Nếu client disconnect, client phải có khả năng gọi REST API để lấy trạng thái order mới nhất.

WebSocket chỉ là phương thức transport cho real-time update.