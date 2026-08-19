package com.example.catalog.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Product catalog entry.
 *
 * Modeling notes:
 * - Stored as a single denormalized document: at catalog scale (hundreds of thousands
 *   of products) and with reads >> writes, a document store shines because a product
 *   detail/list read is satisfied by a single document fetch with no joins.
 * - `price` is a BigDecimal, mapped to BSON Decimal128 (see MongoConfig) to avoid the
 *   floating point precision issues of `double` for money.
 * - `attributes` is an open Map to accommodate category-specific fields (e.g. "size",
 *   "color", "wattage") without a schema migration every time a new product type shows up.
 * - `version` enables optimistic locking so concurrent updates to the same product don't
 *   silently clobber each other.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
public class Product {

    @Id
    private String id;

    /** Human/merchant-facing unique identifier, distinct from the Mongo _id. */
    @Field("sku")
    private String sku;

    @Field("name")
    private String name;

    @Field("description")
    private String description;

    /**
     * Kept as a simple string for this exercise. In a larger system this would likely be
     * a reference (categoryId) into a small, cacheable Category collection to support
     * hierarchies, but a flat category string keeps filtering simple and index-friendly.
     */
    @Field("category")
    private String category;

    @Field("price")
    private BigDecimal price;

    @Field("currency")
    @Builder.Default
    private String currency = "USD";

    @Field("stockQuantity")
    @Builder.Default
    private int stockQuantity = 0;

    @Field("attributes")
    private Map<String, Object> attributes;

    @Field("imageUrls")
    private List<String> imageUrls;

    /** Soft-delete / visibility flag; deletes are logical, not physical (see DELETE endpoint). */
    @Field("active")
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    @Version
    private Long version;
}
