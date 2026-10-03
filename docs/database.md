# Thiết kế Database

## 1. Database

Sử dụng PostgreSQL.

PostgreSQL là source of truth của hệ thống.

---

## 2. Các bảng chính

### users

Các field dự kiến:

- id
- email
- password_hash
- name
- role
- created_at
- updated_at

### restaurants

Các field dự kiến:

- id
- name
- address
- status
- created_at
- updated_at

### products

Các field dự kiến:

- id
- restaurant_id
- name
- description
- price
- stock
- status
- created_at
- updated_at

### orders

Các field dự kiến:

- id
- user_id
- restaurant_id
- status
- total_amount
- version
- created_at
- updated_at

### order_items

Các field dự kiến:

- id
- order_id
- product_id
- quantity
- unit_price
- subtotal

### payments

Các field dự kiến:

- id
- order_id
- idempotency_key
- amount
- status
- transaction_reference
- created_at
- updated_at

### deliveries

Các field dự kiến:

- id
- order_id
- driver_id
- status
- assigned_at
- completed_at

### order_status_history

Các field dự kiến:

- id
- order_id
- old_status
- new_status
- changed_by
- created_at

### outbox_events

Các field dự kiến:

- id
- aggregate_type
- aggregate_id
- event_type
- payload
- status
- created_at
- published_at

---

## 3. Database Constraints

Database nên enforce các invariant quan trọng khi phù hợp.

Ví dụ:

- Email user phải unique
- Payment idempotency key phải unique
- Foreign key phải hợp lệ
- Product price phải lớn hơn 0
- Order quantity phải lớn hơn 0

---

## 4. Optimistic Locking

Các entity có khả năng xảy ra concurrent update cần xem xét sử dụng Optimistic Locking.

Ví dụ:

```java
@Version
private Long version;
```

Chi tiết implementation sẽ được quyết định trong quá trình phát triển.

---

## 5. Transaction

Các business operation yêu cầu atomicity phải sử dụng database transaction.

Không giả định rằng database transaction có thể rollback Kafka message.

Transaction giữa database và Kafka phải được thiết kế riêng.