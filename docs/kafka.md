# Kafka Design

## 1. Mục đích

Kafka được sử dụng cho asynchronous event-driven communication.

Kafka không thay thế PostgreSQL làm source of truth.

Kafka được đưa vào từ Phase 6. Phase 3 không dùng Kafka. Phase 5 implement synchronous flow trước để hiểu coupling.

---

## 2. Topics

### order-events

Tất cả order-related events sử dụng topic `order-events` với Kafka key = `orderId`.

Specific domain events (phục vụ domain/integration use cases):

- OrderCreated
- OrderConfirmed
- OrderCancelled

Event phục vụ notification/WebSocket:

- OrderStatusChanged

Specific event và OrderStatusChanged có thể được publish cho cùng một state transition.

Ví dụ khi Order chuyển CREATED → CONFIRMED:

```text
OrderConfirmed
OrderStatusChanged
```

[NEEDS DESIGN] Hai event của cùng một transition được publish như thế nào (thứ tự, mỗi event có eventId riêng).

### payment-events

- PaymentCompleted
- PaymentFailed

[NEEDS DESIGN] Kafka key cho payment-events.

[NEEDS DESIGN] Có cần event cho payment EXPIRED/REFUNDED hay không.

### delivery-events

- DriverAssigned
- OrderDelivered

[NEEDS DESIGN] Kafka key cho delivery-events.

[NEEDS DESIGN] Có cần event cho các delivery state khác (DRIVER_ACCEPTED, re-assign, ...) hay không.

### notification-events

[NEEDS DESIGN] Có cần topic này hay không, vì OrderStatusChanged trên order-events đã phục vụ notification/WebSocket.

---

## 3. Event Structure

Event cần chứa đủ thông tin để consumer có thể xử lý một cách độc lập.

Ví dụ:

```json
{
  "eventId": "0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b",
  "eventType": "OrderStatusChanged",
  "aggregateId": "0190a1b2-0000-7000-8000-000000000001",
  "occurredAt": "2026-01-01T10:00:00Z",
  "version": 1,
  "payload": {}
}
```

- `eventId`: UUIDv7, dùng cho consumer idempotency.
- `aggregateId`: UUID của aggregate (ví dụ orderId).

[NEEDS DESIGN] Ý nghĩa của `version`: schema version của event hay aggregate version (orders.version). Aggregate version hữu ích để consumer/client bỏ qua event cũ.

---

## 4. Các concept cần nghiên cứu

Project phải giúp hiểu được:

- Producer
- Consumer
- Consumer Group
- Partition
- Offset
- Key
- Ordering
- Retry
- Dead Letter Topic
- Consumer Idempotency

---

## 5. Delivery Guarantee

Sử dụng At-Least-Once Delivery.

Consumer có thể nhận cùng một event nhiều lần.

Consumer phải idempotent và xử lý duplicate event mà không gây ra side effect sai.

[NEEDS DESIGN] Cơ chế consumer idempotency (bảng processed_events, kiểm tra theo state machine, hoặc kết hợp).

---

## 6. Partitioning và Ordering

Các event thuộc cùng một Order sử dụng key = `orderId` trên topic `order-events`, nên thứ tự được giữ trong cùng partition.

Kafka KHÔNG đảm bảo ordering giữa các topic khác nhau (order-events, payment-events, delivery-events).

[NEEDS DESIGN] Cách consumer xử lý event đến không đúng thứ tự giữa các topic.

---

## 7. Failure Handling

Cần xem xét:

- Consumer bị crash
- Database tạm thời unavailable
- Event không hợp lệ
- Duplicate event
- Kafka unavailable
- Consumer restart
- Processing timeout

Retry và Dead Letter strategy phải được thiết kế trước khi implementation.

[NEEDS DESIGN] Retry strategy (số lần, backoff) và Dead Letter Topic naming.

---

## 8. Publish trước khi có Outbox (Phase 6 – Phase 8)

Trước khi có Outbox, publish Kafka có vấn đề dual-write:

```text
Database transaction thành công
        |
        X
Kafka publish thất bại
```

Đây là vấn đề được chấp nhận có chủ đích để hiểu trước khi học Outbox.

[NEEDS DESIGN] Thời điểm publish trong Phase 6 (ví dụ sau khi transaction commit) để tránh publish event của transaction đã rollback.

---

## 9. Outbox Pattern (Phase 9)

Outbox Pattern được triển khai ở Phase 9.

Approach dự kiến:

```text
Business Transaction
        |
        +---- Business Data
        |
        +---- Outbox Event
                    |
                    v
               Outbox Publisher
                    |
                    v
                  Kafka
```

Không triển khai Outbox trước Phase 9.
