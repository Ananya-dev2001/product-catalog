package com.example.catalog.service;

import com.example.catalog.exception.InvalidCursorException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque keyset-pagination cursor encoding the (price, id) of the last item on the
 * previous page. Clients treat this as an opaque token -- we base64-encode it so the
 * sort key isn't presented as "an id you could tamper with" in the API contract, and so
 * the internal cursor shape (e.g. adding a tiebreaker field) can evolve without breaking
 * the wire format.
 */
public record ProductCursor(BigDecimal price, String id) {

    public String encode() {
        String raw = price.toPlainString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static ProductCursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf('|');
            BigDecimal price = new BigDecimal(raw.substring(0, sep));
            String id = raw.substring(sep + 1);
            return new ProductCursor(price, id);
        } catch (Exception e) {
            throw new InvalidCursorException("Cursor is malformed or expired");
        }
    }
}
