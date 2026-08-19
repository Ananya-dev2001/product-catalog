package com.example.catalog.repository;

import com.example.catalog.model.Product;
import com.example.catalog.service.ProductCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<Product> findPageByFilters(String category,
                                            BigDecimal minPrice,
                                            BigDecimal maxPrice,
                                            ProductCursor cursor,
                                            int limit) {
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(Criteria.where("active").is(true));

        if (category != null && !category.isBlank()) {
            conditions.add(Criteria.where("category").is(category));
        }

        if (minPrice != null) {
            conditions.add(Criteria.where("price").gte(minPrice));
        }
        if (maxPrice != null) {
            conditions.add(Criteria.where("price").lte(maxPrice));
        }

        // Keyset condition: (price > cursor.price) OR (price == cursor.price AND id > cursor.id)
        // This is what lets us resume exactly where the previous page left off using only
        // indexed range scans -- no skip(), so performance is flat regardless of how deep
        // into the result set the client pages.
        if (cursor != null) {
            conditions.add(new Criteria().orOperator(
                        Criteria.where("price").gt(cursor.price()),
                        Criteria.where("price").is(cursor.price()).and("id").gt(cursor.id())
            ));
        }

        Criteria combined = new Criteria().andOperator(conditions.toArray(new Criteria[0]));

        Query query = new Query(combined)
                .with(Sort.by(Sort.Order.asc("price"), Sort.Order.asc("_id")))
                .limit(limit);

        query.fields()
            .include("sku")
            .include("name")
            .include("category")
            .include("price")
            .include("currency")
            .include("active");

        return mongoTemplate.find(query, Product.class);
    }
}
