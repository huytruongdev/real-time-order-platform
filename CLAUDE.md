# Context dự án

## Mục tiêu

Đây là project dùng để học và nghiên cứu Java Backend theo hướng production.

Công nghệ chính:

- Java 21
- Spring Boot 3
- Maven
- PostgreSQL
- Flyway
- Redis
- Kafka
- WebSocket
- Spring Security (JWT Access Token + Refresh Token)
- Docker
- JUnit 5
- Mockito
- AssertJ
- Spring Boot Test
- Testcontainers

## Lệnh

Yêu cầu: JDK 21, Docker đang chạy (Testcontainers và docker-compose).

```text
mvnw.cmd test                      # chạy toàn bộ test (Windows; Linux/macOS: ./mvnw test)
docker compose up -d               # khởi động PostgreSQL local
mvnw.cmd spring-boot:run           # chạy application (cần PostgreSQL local)
```

## Nguồn quyết định

Các quyết định kiến trúc và business đã chốt nằm trong `docs/decisions.md`.

Không mở lại các quyết định đã chốt, trừ khi phát hiện mâu thuẫn kỹ thuật nghiêm trọng.

Khi docs ghi `[NEEDS DESIGN]` hoặc `[NEEDS DECISION]`, phải đề xuất phương án và chờ xác nhận, không tự quyết định.

## Kiến trúc

Modular Monolith.

Không sử dụng Microservices. Không tự ý tách service.

Các module chính:

- user
- catalog (Restaurant, Product, Inventory)
- order
- payment
- delivery
- notification

Quy tắc boundary:

- Một module không truy cập trực tiếp Repository hoặc Entity của module khác.
- Module chỉ gọi nhau qua public application/domain interface.
- Ví dụ: Order không dùng `ProductRepository`/`Product` của Catalog, chỉ gọi interface inventory của Catalog.

Các ràng buộc theo phase:

- Phase 3: chưa dùng Kafka.
- Phase 5: implement synchronous flow trước để hiểu coupling.
- Phase 6: đưa Kafka vào.
- Phase 9: Outbox Pattern.

## Nguyên tắc làm việc

Khi gặp một feature phức tạp:

1. Phân tích yêu cầu.
2. Kiểm tra code hiện tại.
3. Đề xuất phương án.
4. Giải thích trade-off.
5. Chỉ sau đó mới implement.
6. Viết test.
7. Review lại implementation.

Không tự ý tạo một lượng lớn code khi chưa thống nhất
phương án.

## Chế độ học tập

Đây là project học tập.

Khi sử dụng một concept quan trọng như:

- Kafka
- Redis
- WebSocket
- Transaction
- Concurrency
- Idempotency
- Distributed Lock
- Outbox Pattern

hãy giải thích:

1. Nó giải quyết vấn đề gì?
2. Vì sao project cần nó?
3. Nó hoạt động như thế nào?
4. Có những failure scenario nào?
5. Vì sao chọn cách triển khai này?
