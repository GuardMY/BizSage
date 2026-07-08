package com.bizsage.api.common;

import java.util.List;

/**
 * V2: Paginated API response wrapper.
 *
 * <p>All list endpoints should return this instead of a bare {@code List<T>}
 * when the total result set can grow beyond a few dozen items.
 */
public record PagedResponse<T>(
    List<T> items,
    int page,
    int size,
    long total,
    boolean hasMore) {

  public static <T> PagedResponse<T> of(List<T> items, int page, int size, long total) {
    return new PagedResponse<>(items, page, size, total, page * size + items.size() < total);
  }
}
