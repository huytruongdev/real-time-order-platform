/**
 * Payment module: payment state machine, idempotency, expiration, refund.
 *
 * Package con: api (REST), application (public interface cho module khác),
 * domain (entity, rule), infrastructure (repository, adapter).
 * Module khác chỉ được phụ thuộc vào package application.
 */
package com.realtimeorder.payment;
