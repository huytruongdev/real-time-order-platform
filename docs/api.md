# API Design

API design ban đầu chỉ định nghĩa các endpoint chính.

Request/Response schema chi tiết sẽ được thiết kế trong quá trình implementation.

---

## Authentication

POST /api/v1/auth/register

POST /api/v1/auth/login

---

## Restaurants

GET /api/v1/restaurants

GET /api/v1/restaurants/{id}

GET /api/v1/restaurants/{id}/products

---

## Orders

POST /api/v1/orders

GET /api/v1/orders/{id}

GET /api/v1/orders

POST /api/v1/orders/{id}/cancel

---

## Payments

POST /api/v1/orders/{id}/payments

GET /api/v1/orders/{id}/payments

---

## Delivery

GET /api/v1/deliveries/{id}

POST /api/v1/deliveries/{id}/accept

POST /api/v1/deliveries/{id}/status

---

## WebSocket

WebSocket endpoint và STOMP destinations được định nghĩa trong docs/websocket.md.