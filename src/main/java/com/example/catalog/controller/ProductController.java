package com.example.catalog.controller;

import com.example.catalog.dto.CursorPage;
import com.example.catalog.dto.ProductRequest;
import com.example.catalog.dto.ProductResponse;
import com.example.catalog.dto.ProductListResponse;
import com.example.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog CRUD, listing and filtering")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "List/search products with optional category and price-range filters (cursor-paginated)")
    @GetMapping
    public ResponseEntity<CursorPage<ProductListResponse>> list(
            @Parameter(description = "Exact category match") @RequestParam(required = false) String category,
            @Parameter(description = "Inclusive minimum price") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Inclusive maximum price") @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Opaque cursor from a previous page's nextCursor") @RequestParam(required = false) String cursor,
            @Parameter(description = "Page size, 1-100, default 20") @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(productService.list(category, minPrice, maxPrice, cursor, limit));
    }

    @Operation(summary = "Get a single product by id")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @Operation(summary = "Create a product")
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + created.getId())).body(created);
    }

    @Operation(summary = "Replace a product's fields")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable String id, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @Operation(summary = "Soft-delete a product (marks it inactive; it stops appearing in listings)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        productService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
