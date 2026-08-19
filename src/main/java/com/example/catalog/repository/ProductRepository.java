package com.example.catalog.repository;

import com.example.catalog.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String>, ProductRepositoryCustom {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);
}
