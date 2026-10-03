# Kafka Design

## 1. Mục đích

Kafka được sử dụng cho asynchronous event-driven communication.

Kafka không thay thế PostgreSQL làm source of truth.

---

## 2. Topics

### order-events

Các event:

- OrderCreated
- OrderConfirmed
- OrderCancelled

### payment-events

Các event:

- PaymentCompleted
- PaymentFailed

### delivery-events

Các event:

- DriverAssigned
- OrderDelivered

### notification-events

Các event dùng để trigger notification đến user.

---

## 3. Event Structure

Event cần chứa đủ thông tin để consumer có thể xử lý một cách độc lập.

Ví dụ:

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "aggregateId": "123",
  "occurredAt": "2026-01-01T10:00:00Z",
  "version": 1,
  "payload": {}
}
```

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

Thiết kế ban đầu sử dụng At-Least-Once Delivery.

Điều này có nghĩa consumer có thể nhận cùng một event nhiều lần.

Consumer phải có khả năng xử lý duplicate event mà không gây ra side effect sai.

---

## 6. Partitioning

Các event thuộc cùng một aggregate nên sử dụng cùng partition key khi cần đảm bảo ordering.

Ví dụ:

```text
orderId
```

Điều này giúp duy trì thứ tự event đối với một order.

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

---

## 8. Outbox Pattern

Project sẽ nghiên cứu và triển khai Outbox Pattern ở phase sau.

Mục tiêu là xử lý vấn đề:

```text
Database transaction thành công
        |
        X
Kafka publish thất bại
```

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

Không triển khai Outbox ngay từ đầu.

Trước tiên cần hiểu Kafka producer/consumer cơ bản, sau đó mới triển khai Outbox Pattern.