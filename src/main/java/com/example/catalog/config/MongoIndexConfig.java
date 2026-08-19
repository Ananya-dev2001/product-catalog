package com.example.catalog.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Creates indexes on application startup instead of relying on implicit/auto index
 * creation. In a real production rollout these would instead live in a migration tool
 * (Mongock/Flyway-style) so index changes are versioned and reviewed, but for this
 * exercise, ensureIndex on boot keeps the whole thing runnable with a single command.
 *
 * Index design, tied directly to the access patterns the task calls out:
 *
 * 1. { sku: 1 } unique
 *    - Enforces catalog SKU uniqueness; also the fastest lookup path for "get by SKU".
 *
 * 2. { active: 1, category: 1, price: 1, _id: 1 }
 *    - The primary listing/filter index. Almost every browse/search request will filter
 *      on `active` (only show live products) and optionally `category`, then range-filter
 *      and/or sort on `price`, with `_id` as a final tiebreaker for stable cursor pagination.
 *      Mongo can satisfy "category=X AND price BETWEEN a AND b, sorted by price" entirely
 *      from this index (index-only scan for the filter/sort, one doc fetch per result page).
 *
 * 3. { active: 1, price: 1, _id: 1 }
 *    - Supports "browse all categories, filter by price" without a category predicate,
 *      where index #2 avoids scanning category ranges when no category predicate exists.
 *
 * With reads vastly outnumbering writes, we deliberately trade a bit of write-side index
 * maintenance cost for read speed -- exactly the right tradeoff for a browse-heavy catalog.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;
    private static final String COLLECTION = "products";

    @EventListener(ApplicationReadyEvent.class)
    public void initIndexes() {
        IndexOperations indexOps = mongoTemplate.indexOps(COLLECTION);

        indexOps.ensureIndex(new Index().on("sku", Sort.Direction.ASC).unique().named("uniq_sku"));

        indexOps.ensureIndex(
                new Index()
                        .on("active", Sort.Direction.ASC)
                        .on("category", Sort.Direction.ASC)
                        .on("price", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC)
                        .named("idx_active_category_price_id")
        );

        indexOps.ensureIndex(
                new Index()
                        .on("active", Sort.Direction.ASC)
                        .on("price", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC)
                        .named("idx_active_price_id")
        );

        log.info("Ensured MongoDB indexes on '{}' collection", COLLECTION);
    }
}
