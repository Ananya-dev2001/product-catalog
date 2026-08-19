package com.example.catalog.dto;

import com.example.catalog.model.Product;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class ProductListResponse {
    String id;
    String sku;
    String name;
    String category;
    BigDecimal price;
    String currency;
    boolean active;

    public static ProductListResponse from(Product product) {
        return ProductListResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .category(product.getCategory())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .active(product.isActive())
                .build();
    }
}
