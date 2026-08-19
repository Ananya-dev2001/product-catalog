package com.example.catalog.repository;

import com.example.catalog.model.Product;
import com.example.catalog.service.ProductCursor;

import java.math.BigDecimal;
import java.util.List;

public interface ProductRepositoryCustom {

    /**
     * Returns up to `limit + 1` products matching the given filters, sorted by
     * (price, _id) ascending, starting strictly after `cursor` if provided.
     * Fetching one extra row lets the caller cheaply determine hasMore without a
     * separate count query.
     */
    List<Product> findPageByFilters(String category,
                                     BigDecimal minPrice,
                                     BigDecimal maxPrice,
                                     ProductCursor cursor,
                                     int limit);
}
