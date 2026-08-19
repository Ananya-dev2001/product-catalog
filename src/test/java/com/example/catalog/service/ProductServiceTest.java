package com.example.catalog.service;

import com.example.catalog.dto.CursorPage;
import com.example.catalog.dto.ProductRequest;
import com.example.catalog.dto.ProductResponse;
import com.example.catalog.dto.ProductListResponse;
import com.example.catalog.exception.InvalidCursorException;
import com.example.catalog.exception.DuplicateSkuException;
import com.example.catalog.exception.ProductNotFoundException;
import com.example.catalog.model.Product;
import com.example.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private ProductRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = ProductRequest.builder()
                .sku("SKU-1")
                .name("Wireless Mouse")
                .category("electronics")
                .price(new BigDecimal("29.99"))
                .stockQuantity(100)
                .build();
    }

    @Test
    void create_savesProduct_whenSkuIsUnique() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId("generated-id");
            return p;
        });

        ProductResponse response = productService.create(validRequest);

        assertThat(response.getId()).isEqualTo("generated-id");
        assertThat(response.getSku()).isEqualTo("SKU-1");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void create_throws_whenSkuAlreadyExists() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(validRequest))
                .isInstanceOf(DuplicateSkuException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById("missing"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void delete_softDeletes_ratherThanRemoving() {
        Product existing = Product.builder().id("p1").sku("SKU-1").active(true).build();
        when(productRepository.findById("p1")).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.delete("p1");

        verify(productRepository).save(argThat(p -> !p.isActive()));
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void list_rejectsInvalidPriceRange() {
        assertThatThrownBy(() ->
                productService.list("electronics", new BigDecimal("50"), new BigDecimal("10"), null, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void list_setsHasMoreAndNextCursor_whenExtraRowFetched() {
        Product p1 = Product.builder().id("1").price(new BigDecimal("10.00")).build();
        Product p2 = Product.builder().id("2").price(new BigDecimal("12.00")).build();
        // service asks for limit+1; repository returns 2 rows for a requested limit of 1
        when(productRepository.findPageByFilters(any(), any(), any(), any(), eq(2)))
                .thenReturn(List.of(p1, p2));

        CursorPage<ProductListResponse> page = productService.list(null, null, null, null, 1);

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getNextCursor()).isNotBlank();
    }

    @Test
    void list_noNextCursor_whenLastPage() {
        Product p1 = Product.builder().id("1").price(new BigDecimal("10.00")).build();
        when(productRepository.findPageByFilters(any(), any(), any(), any(), eq(21)))
                .thenReturn(List.of(p1));

        CursorPage<ProductListResponse> page = productService.list(null, null, null, null, null);

        assertThat(page.isHasMore()).isFalse();
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void update_preservesInactiveState_whenActiveIsOmitted() {
        Product existing = Product.builder()
                .id("p1")
                .sku("SKU-1")
                .name("Old name")
                .category("electronics")
                .price(new BigDecimal("10.00"))
                .active(false)
                .build();
        when(productRepository.findById("p1")).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.update("p1", validRequest);

        assertThat(response.isActive()).isFalse();
    }

    @Test
    void list_rejectsCursorForDifferentFilters() {
        Product product = Product.builder().id("1").price(new BigDecimal("10.00")).build();
        when(productRepository.findPageByFilters(any(), any(), any(), any(), eq(2)))
                .thenReturn(List.of(product, product));

        CursorPage<ProductListResponse> firstPage = productService.list("electronics", null, null, null, 1);

        assertThatThrownBy(() -> productService.list("home", null, null, firstPage.getNextCursor(), 1))
                .isInstanceOf(InvalidCursorException.class);
    }
}
