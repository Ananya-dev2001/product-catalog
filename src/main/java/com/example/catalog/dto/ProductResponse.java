package com.example.catalog.dto;

import com.example.catalog.model.Product;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class ProductResponse {
    String id;
    String sku;
    String name;
    String description;
    String category;
    BigDecimal price;
    String currency;
    int stockQuantity;
    Map<String, Object> attributes;
    List<String> imageUrls;
    boolean active;
    Instant createdAt;
    Instant updatedAt;

    public static ProductResponse from(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .sku(p.getSku())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .price(p.getPrice())
                .currency(p.getCurrency())
                .stockQuantity(p.getStockQuantity())
                .attributes(p.getAttributes())
                .imageUrls(p.getImageUrls())
                .active(p.isActive())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
