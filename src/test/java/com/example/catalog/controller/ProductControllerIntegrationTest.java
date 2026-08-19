package com.example.catalog.controller;

import com.example.catalog.dto.CursorPage;
import com.example.catalog.dto.ProductRequest;
import com.example.catalog.dto.ProductResponse;
import com.example.catalog.dto.ProductListResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test against a real MongoDB instance (via Testcontainers) to validate that
 * the repository queries, indexes, and cursor-pagination logic behave correctly together --
 * not just that each layer works in isolation with mocks.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductControllerIntegrationTest {

    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @BeforeAll
    static void startContainer() {
        MONGO.start();
    }

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("catalog-test"));
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/products";
    }

    @Test
    void fullCrudLifecycle() {
        ProductRequest request = ProductRequest.builder()
                .sku("SKU-CRUD-1")
                .name("Mechanical Keyboard")
                .category("electronics")
                .price(new BigDecimal("89.99"))
                .stockQuantity(50)
                .build();

        // CREATE
        ResponseEntity<ProductResponse> createResp =
                rest.postForEntity(baseUrl(), request, ProductResponse.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = createResp.getBody().getId();
        assertThat(id).isNotBlank();

        // READ
        ResponseEntity<ProductResponse> getResp =
                rest.getForEntity(baseUrl() + "/" + id, ProductResponse.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().getName()).isEqualTo("Mechanical Keyboard");

        // UPDATE
        ProductRequest updateReq = ProductRequest.builder()
                .sku("SKU-CRUD-1")
                .name("Mechanical Keyboard - RGB Edition")
                .category("electronics")
                .price(new BigDecimal("99.99"))
                .stockQuantity(40)
                .build();
        rest.put(baseUrl() + "/" + id, updateReq);

        ResponseEntity<ProductResponse> afterUpdate =
                rest.getForEntity(baseUrl() + "/" + id, ProductResponse.class);
        assertThat(afterUpdate.getBody().getName()).contains("RGB Edition");
        assertThat(afterUpdate.getBody().getPrice()).isEqualByComparingTo("99.99");

        // DELETE (soft)
        rest.delete(baseUrl() + "/" + id);
        ResponseEntity<ProductResponse> afterDelete =
                rest.getForEntity(baseUrl() + "/" + id, ProductResponse.class);
        // still fetchable by id, but no longer active
        assertThat(afterDelete.getBody().isActive()).isFalse();
    }

    @Test
    void create_rejectsDuplicateSku() {
        ProductRequest request = ProductRequest.builder()
                .sku("SKU-DUP")
                .name("Product A")
                .category("home")
                .price(new BigDecimal("10.00"))
                .build();

        rest.postForEntity(baseUrl(), request, ProductResponse.class);
        ResponseEntity<String> second = rest.postForEntity(baseUrl(), request, String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void list_filtersByCategoryAndPriceRange_andPaginatesWithoutDuplicatesOrGaps() {
        // Seed 25 products in a dedicated category so pagination assertions are deterministic.
        for (int i = 0; i < 25; i++) {
            ProductRequest r = ProductRequest.builder()
                    .sku("SKU-PAGE-" + i)
                    .name("Item " + i)
                    .category("pagination-test")
                    .price(new BigDecimal(10 + i)) // 10..34
                    .build();
            rest.postForEntity(baseUrl(), r, ProductResponse.class);
        }

        Set<String> seenIds = new HashSet<>();
        String cursor = null;
        int totalFetched = 0;
        int pages = 0;

        do {
            String url = baseUrl() + "?category=pagination-test&minPrice=15&maxPrice=30&limit=7"
                    + (cursor != null ? "&cursor=" + cursor : "");
                ResponseEntity<CursorPage<ProductListResponse>> resp = rest.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    });
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

                CursorPage<ProductListResponse> page = resp.getBody();
                for (ProductListResponse pr : page.getItems()) {
                assertThat(seenIds.add(pr.getId())).as("no duplicate items across pages").isTrue();
                assertThat(pr.getPrice()).isBetween(new BigDecimal("15"), new BigDecimal("30"));
            }
            totalFetched += page.getItems().size();
            cursor = page.getNextCursor();
            pages++;
            assertThat(pages).isLessThan(20); // safety valve against infinite loop on a bug
        } while (cursor != null);

        // prices 15..30 inclusive = 16 matching products
        assertThat(totalFetched).isEqualTo(16);
    }
}
