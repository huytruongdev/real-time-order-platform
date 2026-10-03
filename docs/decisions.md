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

---

# Business & Technical Decisions (đã chốt)

Các quyết định dưới đây đã được chốt.
Không mở lại trong các phase sau, trừ khi phát hiện mâu thuẫn kỹ thuật nghiêm trọng.

---

## ADR-006: Payment Flow

**Status: Superseded bởi ADR-021** (bổ sung EXPIRED, retry payment, rule PREPARING).

### Quyết định

Flow:

```text
Customer Create Order
→ Restaurant Confirm
→ Customer Payment
```

Payment states:

- PENDING
- SUCCESS
- FAILED
- REFUNDED

Không thêm trạng thái PAID vào Order.

Order và Payment là hai state machine độc lập.

### Hệ quả

Payment chỉ được tạo sau khi Order đã được Restaurant confirm.

---

## ADR-007: Inventory Reservation

**Status: Superseded bởi ADR-023** (Payment FAILED không còn release reservation).

### Quyết định

Khi Customer tạo Order, product stock được RESERVE.

Thiết kế ưu tiên:

- available_stock
- reserved_stock

Ví dụ:

```text
Ban đầu:          available_stock = 10, reserved_stock = 0
Customer đặt 3:   available_stock = 7,  reserved_stock = 3
```

- Payment SUCCESS: reservation được giữ và chuyển thành sold.
- Order CANCELLED hoặc Payment FAILED: reservation được release.

Nếu Phase 3 cần đơn giản hơn, có thể tạm thời dùng stock decrement và restore stock khi cancel/fail, nhưng code phải được thiết kế để mở rộng sang available/reserved stock.

Database là source of truth cho inventory.

Không sử dụng Redis Lock làm cơ chế chính để chống oversell.

---

## ADR-008: Driver Assignment

**Status: Superseded bởi ADR-025** (bổ sung thời điểm assign và delivery lifecycle).

### Quyết định

System tự động assign driver.

Sau đó Driver accept delivery.

---

## ADR-009: Authentication

**Status: Amended bởi ADR-026** (cách lưu refresh token).

### Quyết định

Sử dụng JWT Access Token + Refresh Token.

Authentication stateless.

---

## ADR-010: ID

**Status: Superseded bởi ADR-027** (UUIDv7, generate ở application layer).

### Quyết định

Sử dụng UUID.

---

## ADR-011: Build Tool

### Quyết định

Sử dụng Maven.

---

## ADR-012: Database Migration

### Quyết định

Sử dụng Flyway.

---

## ADR-013: Architecture

### Quyết định

Modular Monolith.

Không sử dụng Microservices.

Không tự ý tách service.

---

## ADR-014: Modules

**Status: Amended bởi ADR-029** (Catalog sở hữu Inventory).

### Quyết định

Các module chính:

- user
- catalog
- order
- payment
- delivery
- notification

Catalog chịu trách nhiệm Restaurant và Product.

Không đưa Restaurant/Product vào Order module.

---

## ADR-015: Kafka

### Quyết định

Kafka sử dụng At-Least-Once Delivery.

Consumer phải idempotent.

Kafka được đưa vào từ Phase 6. Không dùng Kafka trong Phase 3.

Phase 5 implement synchronous flow trước để hiểu coupling.

---

## ADR-016: WebSocket và OrderStatusChanged

**Status: Amended bởi ADR-028** (quan hệ giữa specific events và OrderStatusChanged, topic, key).

### Quyết định

Có event `OrderStatusChanged` phục vụ real-time update.

WebSocket chỉ là transport, không phải source of truth.

Khi reconnect, client lấy state mới nhất bằng REST API.

---

## ADR-017: Redis

### Quyết định

Redis dùng cho:

- Cache
- Idempotency

Redis không phải source of truth.

Distributed Lock chỉ dùng để nghiên cứu và so sánh với database locking.

Không dùng Redis Lock làm giải pháp mặc định cho inventory.

---

## ADR-018: Testing

### Quyết định

Sử dụng:

- JUnit 5
- Mockito
- AssertJ
- Spring Boot Test
- Testcontainers

Integration test sử dụng PostgreSQL/Redis/Kafka thật thông qua Testcontainers khi phù hợp.

---

## ADR-019: Database

### Quyết định

PostgreSQL là source of truth.

Business-critical state không được chỉ lưu ở Redis hoặc Kafka.

---

## ADR-020: Outbox

### Quyết định

Outbox Pattern được triển khai ở Phase 9.

Không triển khai Outbox ngay từ đầu.

Mục tiêu là trước tiên hiểu vấn đề Kafka dual-write, sau đó mới học Outbox Pattern.

(Bổ sung thời điểm cụ thể cho ADR-005.)

---

## ADR-021: Payment Flow và Payment State

**Status: Accepted. Supersedes ADR-006.**

### Quyết định

Order và Payment là hai state machine độc lập.

Order không có trạng thái PAID.

Payment chỉ được thực hiện sau khi Restaurant confirm (Order CONFIRMED).

Payment phải SUCCESS trước khi Order được chuyển sang PREPARING:

```text
Order CONFIRMED
    ↓
Payment PENDING
    ↓
Payment SUCCESS
    ↓
Order PREPARING
```

Payment states:

- PENDING
- SUCCESS
- FAILED
- REFUNDED
- EXPIRED

Payment FAILED không làm Order kết thúc. Customer được retry payment cho cùng Order:

```text
PENDING → FAILED → retry → PENDING
```

### Open items

- [NEEDS DESIGN] Retry tạo payment record mới hay tái sử dụng record cũ.
- [NEEDS DESIGN] Constraint đảm bảo mỗi Order có tối đa một payment SUCCESS.

---

## ADR-022: Payment Expiration

**Status: Accepted.**

### Quyết định

Sau khi Order chuyển sang CONFIRMED, Customer có một khoảng thời gian để thanh toán.

Thời gian là configurable application property:

```text
payment.expiration-minutes
```

Giá trị mặc định đề xuất: 15 phút (configuration, không hard-code).

Khi hết thời gian:

- Payment → EXPIRED
- Order → CANCELLED
- Inventory reservation được release

Không tạo Order state PAYMENT_EXPIRED.

### Open items

- [NEEDS DESIGN] Cơ chế phát hiện expiration.
- [NEEDS DESIGN] Payment EXPIRED khi Customer chưa từng tạo payment attempt.
- [NEEDS DESIGN] Race condition giữa payment SUCCESS và expiration.

---

## ADR-023: Inventory Reservation Lifecycle

**Status: Accepted. Supersedes ADR-007.**

### Quyết định

Khi Customer tạo Order, stock được RESERVE (available_stock giảm, reserved_stock tăng).

- Payment SUCCESS: reservation được giữ và chuyển thành sold.
- Payment FAILED: reservation KHÔNG được release.

Reservation chỉ được release khi:

- Customer cancel Order hợp lệ
- Payment timeout/expiration
- Order bị system cancel

Database là source of truth cho inventory.

Không sử dụng Redis Lock làm cơ chế chính để chống oversell.

Phase 3 có thể tạm dùng stock decrement/restore, nhưng code phải thiết kế để mở rộng sang available/reserved stock.

### Open items

- [NEEDS DESIGN] Cơ chế chống oversell trong database (atomic update, pessimistic lock, optimistic lock).
- [NEEDS DESIGN] Inventory policy khi return sold quantity sau refund.

---

## ADR-024: Customer Cancel

**Status: Accepted.**

### Quyết định

Customer được phép cancel khi Order ở:

- CREATED
- CONFIRMED

Customer không được tự cancel khi Order ở:

- PREPARING
- READY
- DRIVER_ASSIGNED
- PICKING_UP
- DELIVERING

Nếu Order đã payment SUCCESS và được cancel hợp lệ:

- Payment → REFUNDED
- Inventory reservation/sold quantity được release/return theo inventory policy.

### Open items

- [NEEDS DESIGN] Cancel khi đang có payment PENDING.

---

## ADR-025: Driver Assignment và Delivery Lifecycle

**Status: Accepted. Supersedes ADR-008. Amended bởi ADR-030** (mapping Order ↔ Delivery, driver reject).

### Quyết định

System tự động assign driver khi Order chuyển sang READY.

Delivery flow:

```text
WAITING_FOR_DRIVER
    ↓
DRIVER_ASSIGNED
    ↓
DRIVER_ACCEPTED
    ↓
PICKING_UP
    ↓
DELIVERING
    ↓
DELIVERED
```

Nếu driver không accept trong configured timeout:

```text
DRIVER_ASSIGNED → WAITING_FOR_DRIVER → assign driver khác
```

Nếu không có driver available: Delivery ở WAITING_FOR_DRIVER.

Không tự tạo thêm trạng thái nếu chưa cần thiết.

### Open items

- ~~[NEEDS DECISION] Mapping giữa Delivery status và Order status (Order DRIVER_ASSIGNED/PICKING_UP/DELIVERING/DELIVERED), đặc biệt khi Delivery quay lại WAITING_FOR_DRIVER.~~ → Resolved bởi ADR-030.
- [NEEDS DESIGN] Tiêu chí chọn driver, driver availability, tên property cho accept timeout, cơ chế retry assign.

---

## ADR-026: Refresh Token Storage

**Status: Accepted. Amends ADR-009.**

### Quyết định

Refresh token được lưu server-side trong PostgreSQL.

Không lưu raw refresh token, chỉ lưu hash.

Hỗ trợ:

- rotation
- revoke
- expiration

"Stateless" chỉ áp dụng cho access token.

### Open items

- [NEEDS DESIGN] Thuật toán hash, cấu trúc phục vụ rotation và phát hiện token reuse.
- [NEEDS DESIGN] Thời hạn access token và refresh token.

---

## ADR-027: UUIDv7

**Status: Accepted. Supersedes ADR-010.**

### Quyết định

Sử dụng UUIDv7.

UUID được generate ở application layer.

### Open items

- [NEEDS DESIGN] Thư viện/cách generate UUIDv7.

---

## ADR-028: OrderStatusChanged và Specific Domain Events

**Status: Accepted. Amends ADR-016.**

### Quyết định

Giữ cả:

- Specific domain events (ví dụ OrderConfirmed) phục vụ domain/integration use cases.
- OrderStatusChanged phục vụ notification/WebSocket.

Hai event có thể được publish cho cùng một state transition.

Tất cả order-related events:

- topic = `order-events`
- Kafka key = `orderId`

### Open items

- [NEEDS DESIGN] Kafka key cho payment-events và delivery-events.
- [NEEDS DESIGN] Ý nghĩa field `version` trong event.

---

## ADR-029: Inventory Ownership

**Status: Accepted. Amends ADR-014.**

### Quyết định

Catalog Module sở hữu:

- Product
- Inventory

Order KHÔNG được truy cập trực tiếp ProductRepository hoặc Product entity của Catalog.

Order chỉ gọi public application/domain interface của Catalog, ví dụ:

- reserve(...)
- release(...)
- commit(...)

### Open items

- [NEEDS DESIGN] Chữ ký cụ thể của interface phải được đề xuất và xác nhận trước khi code.

---

## ADR-030: Order vs Delivery Status Mapping

**Status: Accepted. Amends ADR-025. Resolves open item "Mapping Delivery status ↔ Order status".**

### Quyết định

Order chỉ chuyển sang DRIVER_ASSIGNED khi driver đã ACCEPT.

Order KHÔNG được chuyển DRIVER_ASSIGNED → READY khi driver timeout/reject.

Delivery tự xử lý retry assignment:

```text
WAITING_FOR_DRIVER → DRIVER_ASSIGNED → DRIVER_ACCEPTED
```

Nếu driver reject hoặc không accept trong timeout:

```text
DRIVER_ASSIGNED → WAITING_FOR_DRIVER → assign driver khác
```

Trong toàn bộ quá trình này, Order vẫn ở READY cho đến khi driver thực sự ACCEPT.

Khi driver ACCEPT:

- Order: READY → DRIVER_ASSIGNED
- Delivery: DRIVER_ASSIGNED → DRIVER_ACCEPTED

### Lý do

Giữ Order state machine monotonic và không để Order phụ thuộc vào chi tiết retry của Delivery.

### Hệ quả

- Driver có thao tác reject delivery được assign.
- Tên `DRIVER_ASSIGNED` có nghĩa khác nhau: ở Order là "driver đã accept", ở Delivery là "đang chờ driver accept".
- Sau DRIVER_ACCEPTED, Order đi theo Delivery: PICKING_UP → DELIVERING → DELIVERED.

---

## ADR-031: Restaurant Reject

**Status: Accepted.**

### Quyết định

Restaurant chỉ được reject Order khi Order = CREATED.

```text
CREATED → CANCELLED
```

Nếu chưa payment SUCCESS thì không có refund. Vì payment chỉ được tạo sau CONFIRMED, Order bị reject không bao giờ có payment.

### Open items

- [NEEDS DECISION] Restaurant reject có release inventory reservation hay không (ADR-023 chưa liệt kê trường hợp này).

---

## ADR-032: Order không dùng FAILED

**Status: Accepted.**

### Quyết định

Không sử dụng FAILED cho Order ở phiên bản hiện tại.

FAILED chủ yếu là state của Payment hoặc technical processing.

Order sử dụng CANCELLED cho business cancellation.

Không tự thêm Order FAILED nếu chưa có business requirement.

### Hệ quả

Trạng thái kết thúc của Order: DELIVERED, CANCELLED.

---

## ADR-033: System Cancel Scope

**Status: Accepted.**

### Quyết định

Payment expiration là trường hợp system cancel duy nhất ở phiên bản hiện tại:

- Payment → EXPIRED
- Order → CANCELLED
- Inventory reservation → RELEASED

Không tự động cancel Order chỉ vì chưa tìm được driver.

Nếu không có driver: Delivery → WAITING_FOR_DRIVER và tiếp tục retry assignment theo policy được thiết kế ở Delivery phase.

### Open items

- [NEEDS DESIGN] Retry assignment policy.

---

## ADR-034: Phase 0–1 Technical Design

**Status: Accepted.** Giải quyết các [NEEDS DESIGN] của Phase 0 và Phase 1.

### Quyết định

- Package gốc `com.realtimeorder`. Mỗi module có `api / application / domain / infrastructure`.
- Module khác chỉ được phụ thuộc vào package `application` của một module. `shared` không phụ thuộc vào module nào. Enforce bằng ArchUnit test (`ModuleBoundaryTest`).
- UUIDv7 sinh bằng thư viện `java-uuid-generator` (JUG). Entity kế thừa `BaseEntity` (implement `Persistable`) để tránh SELECT thừa trước INSERT.
- Error format: ProblemDetail (RFC 7807) kèm property `code`.
- Kiểu dữ liệu thời gian: `TIMESTAMPTZ`, Java `Instant`. Thời gian lấy qua `Clock` bean.
- Password: BCrypt. Password 8–72 ký tự.
- Self-registration luôn tạo role CUSTOMER.
- Access token: JWT HS256 qua Spring OAuth2 Resource Server (Nimbus). Claim `sub` = userId, `role` = role. TTL `security.jwt.access-token-ttl` (mặc định 15m).
- Refresh token:
  - Chuỗi random 256 bit (opaque, không phải JWT), gửi qua JSON body.
  - Lưu SHA-256 hash.
  - TTL `security.refresh-token.ttl` (mặc định 7d).
  - Rotation mỗi lần refresh. Token cùng một lần login có chung `family_id`.
  - Dùng lại token đã revoke (reuse) thì revoke toàn bộ family.
  - Refresh dùng `SELECT ... FOR UPDATE` để hai request đồng thời không cùng rotate thành công.
  - Logout chỉ revoke token được gửi lên, idempotent.

### Hệ quả

- Access token còn hạn vẫn dùng được sau logout cho đến khi hết hạn (đặc điểm của stateless JWT).
- [NEEDS DESIGN] Cách tạo user có role RESTAURANT/DRIVER/ADMIN (Admin API hoặc seed data).

---

# Open Business Decisions

Đã giải quyết bởi ADR-030 đến ADR-033:

- ~~Mapping Delivery status ↔ Order status~~ → ADR-030
- ~~Restaurant reject~~ → ADR-031
- ~~Order FAILED~~ → ADR-032
- ~~System cancel ngoài payment expiration~~ → ADR-033

Còn lại:

- [NEEDS DECISION] Restaurant reject (CREATED → CANCELLED) có release inventory reservation hay không. Phát hiện khi kiểm tra consistency giữa ADR-023 và ADR-031: nếu không release thì stock bị giữ vĩnh viễn cho Order đã CANCELLED.