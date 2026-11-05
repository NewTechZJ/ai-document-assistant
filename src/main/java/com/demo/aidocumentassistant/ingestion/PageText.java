package com.demo.aidocumentassistant.ingestion;

import java.util.Objects;

/**
 * The cleaned text of one PDF page.
 *
 * @param pageNumber 1-based, matching the number a PDF viewer shows, so citations need no conversion
 * @param text       normalized text; empty string (never null) for a page without text
 */
public record PageText(int pageNumber, String text) {

    public PageText {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber is 1-based, got " + pageNumber);
        }
        Objects.requireNonNull(text, "text");
    }

    public boolean isBlank() {
        return text.isBlank();
    }
}
