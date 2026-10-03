# Redis Design

## 1. Mục đích

Redis được sử dụng cho performance và distributed coordination.

Redis không phải source of truth.

---

## 2. Các use case

### Cache

Ví dụ:

```text
restaurant:{id}
product:{id}
```

Nên sử dụng TTL phù hợp.

---

### Idempotency

Ví dụ:

```text
idempotency:payment:{key}
```

Dùng để hỗ trợ ngăn duplicate request.

---

### Distributed Lock

Có thể sử dụng để xử lý các operation có khả năng xảy ra concurrent update.

Ví dụ:

```text
lock:product:{productId}
```

Cần nghiên cứu:

- Lock expiration
- Process crash khi đang giữ lock
- Unlock ownership
- Race condition
- Lock timeout

---

## 3. Cache Strategy

Ban đầu sử dụng Cache-Aside.

Flow:

```text
Application
    |
    v
  Redis
    |
    | Cache Miss
    v
PostgreSQL
    |
    v
  Redis
```

Cần xử lý cache invalidation khi dữ liệu trong database thay đổi.

---

## 4. Không sử dụng Redis cho

Không sử dụng Redis làm primary source cho:

- Orders
- Payments
- Users
- Financial transactions

Các dữ liệu này phải được lưu trong PostgreSQL.