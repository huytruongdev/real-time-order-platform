# Architecture Decisions

File này ghi lại các quyết định kiến trúc quan trọng trong quá trình phát triển project.

---

## ADR-001: Bắt đầu bằng Modular Monolith

### Quyết định

Project bắt đầu với Modular Monolith.

### Lý do

Mục tiêu chính là học architecture và business boundaries.

Bắt đầu bằng Microservices sẽ đưa thêm operational complexity trước khi hiểu rõ boundary giữa các module.

---

## ADR-002: PostgreSQL là Source of Truth

### Quyết định

PostgreSQL lưu trữ business-critical state.

### Lý do

Redis và Kafka là supporting infrastructure, không phải authoritative business database.

---

## ADR-003: Kafka sử dụng At-Least-Once Delivery

### Quyết định

Giả định Kafka consumer có thể nhận duplicate event.

### Hệ quả

Consumer phải idempotent.

---

## ADR-004: WebSocket chỉ là Real-Time Transport

### Quyết định

WebSocket không lưu business state.

### Lý do

Client có thể disconnect hoặc mất kết nối.

Khi reconnect, client phải lấy trạng thái hiện tại thông qua REST API.

---

## ADR-005: Triển khai Outbox Pattern ở phase sau

### Quyết định

Outbox Pattern chỉ được triển khai sau khi hiểu Kafka producer/consumer cơ bản.

### Lý do

Project phục vụ mục tiêu học tập.

Cần hiểu vấn đề trước khi học solution.