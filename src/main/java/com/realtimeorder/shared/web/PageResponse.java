package com.realtimeorder.shared.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Format response cho endpoint dạng list (offset pagination, ADR-035).
 *
 * Không serialize thẳng {@link Page} của Spring Data: JSON của {@code PageImpl} không phải
 * format ổn định và Spring Data đã cảnh báo không nên dùng làm API contract.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
