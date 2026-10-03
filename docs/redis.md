# Redis Design

## 1. Mục đích

Redis được sử dụng cho:

- Cache
- Idempotency

Redis không phải source of truth.

Mất dữ liệu Redis không được làm sai business state; PostgreSQL vẫn là nguồn chân lý.

---

## 2. Các use case

### Cache

Ví dụ:

```text
restaurant:{id}
product:{id}
```

Nên sử dụng TTL phù hợp.

[NEEDS DESIGN] Giá trị TTL và dữ liệu nào của product được cache (inventory thay đổi thường xuyên, cần cân nhắc có cache available_stock hay không).

---

### Idempotency

Ví dụ:

```text
idempotency:payment:{key}
```

Dùng để hỗ trợ ngăn duplicate request.

Unique constraint trên `payments.idempotency_key` trong PostgreSQL là cơ chế đảm bảo cuối cùng; Redis là lớp hỗ trợ.

[NEEDS DESIGN] Hành vi khi cùng key nhưng khác payload, và khi request đầu tiên với cùng key vẫn đang xử lý.

[NEEDS DESIGN] TTL của idempotency key.

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

[NEEDS DESIGN] Thời điểm invalidate (sau khi transaction commit) và cách xử lý race condition của cache-aside.

---

## 4. Nghiên cứu: Distributed Lock

Distributed Lock chỉ dùng để nghiên cứu và so sánh với database locking.

KHÔNG dùng Redis Lock làm giải pháp mặc định cho inventory. Inventory chống oversell bằng PostgreSQL.

Ví dụ key dùng trong thí nghiệm:

```text
lock:product:{productId}
```

Cần nghiên cứu:

- Lock expiration
- Process crash khi đang giữ lock
- Unlock ownership
- Race condition
- Lock timeout
- So sánh với database locking (atomic update, pessimistic lock, optimistic lock)

---

## 5. Không sử dụng Redis cho

Không sử dụng Redis làm primary source cho:

- Orders
- Payments
- Users
- Refresh tokens
- Inventory
- Financial transactions

Các dữ liệu này phải được lưu trong PostgreSQL.
