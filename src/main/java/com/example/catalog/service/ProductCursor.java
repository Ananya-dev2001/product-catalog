package com.example.catalog.service;

import com.example.catalog.exception.InvalidCursorException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.Instant;

/**
 * Opaque keyset-pagination cursor containing the last sort key, query identity, and
 * expiration time. Encoding keeps those implementation details out of the API contract;
 * the service validates the query identity and expiry before using the cursor.
 */
public record ProductCursor(BigDecimal price, String id, String filterKey, Instant expiresAt) {

    public String encode() {
        String raw = String.join(".",
                encodePart(price.toPlainString()),
                encodePart(id),
                encodePart(filterKey),
                encodePart(expiresAt.toString()));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static ProductCursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\.", -1);
            if (parts.length != 4) throw new IllegalArgumentException("wrong cursor version");
            BigDecimal price = new BigDecimal(decodePart(parts[0]));
            String id = decodePart(parts[1]);
            String filterKey = decodePart(parts[2]);
            Instant expiresAt = Instant.parse(decodePart(parts[3]));
            if (id.isBlank() || filterKey.isBlank()) throw new IllegalArgumentException("empty cursor field");
            return new ProductCursor(price, id, filterKey, expiresAt);
        } catch (Exception e) {
            throw new InvalidCursorException("Cursor is malformed or expired");
        }
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    private static String encodePart(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
