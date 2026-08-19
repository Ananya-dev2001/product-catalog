package com.example.catalog.service;

import com.example.catalog.dto.CursorPage;
import com.example.catalog.dto.ProductRequest;
import com.example.catalog.dto.ProductResponse;
import com.example.catalog.dto.ProductListResponse;
import com.example.catalog.exception.DuplicateSkuException;
import com.example.catalog.exception.InvalidCursorException;
import com.example.catalog.exception.ProductNotFoundException;
import com.example.catalog.model.Product;
import com.example.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final Duration CURSOR_TTL = Duration.ofMinutes(15);

    private final ProductRepository productRepository;

    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }
        Product product = toEntity(request, null);
        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    public ProductResponse getById(String id) {
        return ProductResponse.from(findOrThrow(id));
    }

    public ProductResponse update(String id, ProductRequest request) {
        Product existing = findOrThrow(id);

        // If the SKU is changing, make sure the new one isn't already taken by another product.
        if (!existing.getSku().equals(request.getSku()) && productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }

        Product updated = toEntity(request, existing.getId());
        updated.setCreatedAt(existing.getCreatedAt());
        if (request.getActive() == null) {
            updated.setActive(existing.isActive());
        }
        updated.setVersion(existing.getVersion()); // preserves optimistic-locking check on save

        Product saved = productRepository.save(updated);
        return ProductResponse.from(saved);
    }

    public void delete(String id) {
        Product existing = findOrThrow(id);
        // Soft delete: flip `active` off rather than physically removing the document.
        // Keeps order history / analytics referencing this product intact, and is cheap
        // to filter out of listing queries (see `active` in the compound indexes).
        existing.setActive(false);
        productRepository.save(existing);
    }

    public CursorPage<ProductListResponse> list(String category, BigDecimal minPrice, BigDecimal maxPrice,
                                                String cursorToken, Integer limitParam) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice must be <= maxPrice");
        }

        int limit = normalizeLimit(limitParam);
        String filterKey = filterKey(category, minPrice, maxPrice);
        ProductCursor cursor = (cursorToken != null && !cursorToken.isBlank())
                ? ProductCursor.decode(cursorToken)
                : null;
        if (cursor != null && (cursor.isExpired() || !filterKey.equals(cursor.filterKey()))) {
            throw new InvalidCursorException("Cursor does not match this query or has expired");
        }

        // Fetch one extra row so we can tell whether another page exists without a
        // separate (and, at this scale, expensive) count query.
        List<Product> rows = productRepository.findPageByFilters(category, minPrice, maxPrice, cursor, limit + 1);

        boolean hasMore = rows.size() > limit;
        List<Product> pageRows = hasMore ? rows.subList(0, limit) : rows;

        String nextCursor = null;
        if (hasMore) {
            Product last = pageRows.get(pageRows.size() - 1);
            nextCursor = new ProductCursor(last.getPrice(), last.getId(), filterKey,
                    java.time.Instant.now().plus(CURSOR_TTL)).encode();
        }

        List<ProductListResponse> items = pageRows.stream().map(ProductListResponse::from).toList();

        return CursorPage.<ProductListResponse>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .limit(limit)
                .build();
    }

    private int normalizeLimit(Integer requested) {
        if (requested == null) return DEFAULT_LIMIT;
        if (requested < 1) throw new IllegalArgumentException("limit must be >= 1");
        return Math.min(requested, MAX_LIMIT);
    }

    private String filterKey(String category, BigDecimal minPrice, BigDecimal maxPrice) {
        String normalizedCategory = category == null || category.isBlank() ? "" : category;
        return normalizedCategory + "|"
                + (minPrice == null ? "" : minPrice.toPlainString()) + "|"
                + (maxPrice == null ? "" : maxPrice.toPlainString());
    }

    private Product findOrThrow(String id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Product toEntity(ProductRequest r, String existingId) {
        return Product.builder()
                .id(existingId)
                .sku(r.getSku())
                .name(r.getName())
                .description(r.getDescription())
                .category(r.getCategory())
                .price(r.getPrice())
                .currency(r.getCurrency() != null ? r.getCurrency() : "USD")
                .stockQuantity(r.getStockQuantity())
                .attributes(r.getAttributes())
                .imageUrls(r.getImageUrls())
                .active(r.getActive() == null || r.getActive())
                .build();
    }
}
