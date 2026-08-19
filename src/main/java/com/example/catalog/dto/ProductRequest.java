package com.example.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "sku is required")
    private String sku;

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    @NotBlank(message = "category is required")
    private String category;

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "price must be >= 0")
    private BigDecimal price;

    private String currency;

    @Min(value = 0, message = "stockQuantity must be >= 0")
    private int stockQuantity;

    private Map<String, Object> attributes;

    private List<String> imageUrls;

    private Boolean active;
}
