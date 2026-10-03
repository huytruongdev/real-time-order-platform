# Testing Strategy

## 1. Unit Test

Test business logic độc lập.

Ví dụ:

- Order state transition
- Price calculation
- Payment idempotency
- Validation

---

## 2. Integration Test

Test integration với:

- PostgreSQL
- Redis
- Kafka

Ưu tiên sử dụng Testcontainers cho infrastructure-dependent tests.

---

## 3. Concurrency Test

Các scenario quan trọng:

- Duplicate payment
- Concurrent inventory update
- Concurrent order status update

---

## 4. Kafka Test

Cần test:

- Event publishing
- Event consumption
- Duplicate event
- Retry
- Dead Letter handling

---

## 5. WebSocket Test

Cần test:

- Authentication
- Authorization
- Subscription
- Order update delivery
- Reconnection behavior khi phù hợp

---

## 6. Testing Principle

Không viết test chỉ để tăng code coverage.

Test phải bảo vệ:

- Business rules
- Important edge cases
- Failure scenarios
- Concurrency behavior
- Integration behavior