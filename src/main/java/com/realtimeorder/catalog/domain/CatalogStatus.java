package com.realtimeorder.catalog.domain;

/**
 * Trạng thái hiển thị của Restaurant và Product.
 *
 * Không xoá cứng: order_items (Phase 3) sẽ tham chiếu product_id, nên "xoá" là chuyển INACTIVE.
 * API public chỉ trả về ACTIVE.
 */
public enum CatalogStatus {
    ACTIVE,
    INACTIVE
}
