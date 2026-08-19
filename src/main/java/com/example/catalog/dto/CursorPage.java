package com.example.catalog.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Envelope for keyset ("cursor") pagination.
 *
 * We deliberately do NOT expose a `totalCount` or `totalPages` field: computing an exact
 * count over a filtered few-hundred-thousand-row collection on every list request is an
 * expensive COLLSCAN-adjacent operation that would undermine the whole point of indexed
 * cursor pagination. If the frontend needs an approximate total (e.g. "About 12,400 results"),
 * that should come from a separate, cached/async count endpoint -- not be recomputed on
 * every page.
 */
@Value
@Builder
public class CursorPage<T> {
    List<T> items;
    String nextCursor;   // null when there are no more results
    boolean hasMore;
    int limit;
}
