package com.realtimeorder.catalog.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Kết quả reserve của một dòng, kèm snapshot tên và giá tại thời điểm reserve.
 * Order lưu snapshot này để giá trong order không đổi khi Admin sửa giá product sau đó.
 */
public record ReservedItem(UUID productId, String productName, BigDecimal unitPrice, int quantity) {
}
