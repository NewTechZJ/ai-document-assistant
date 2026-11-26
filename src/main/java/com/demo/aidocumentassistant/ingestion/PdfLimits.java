/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * PDF extraction limits, under "app.pdf".
 *
 * @param maxPages           maximum number of pages accepted
 * @param maxTextChars       maximum characters of extracted text (about 600-700 dense pages)
 * @param maxExtractionTime  maximum time allowed for text extraction
 */
@ConfigurationProperties("app.pdf")
public record PdfLimits(
        @DefaultValue("500") int maxPages,
        @DefaultValue("2000000") int maxTextChars,
        @DefaultValue("60s") Duration maxExtractionTime
) {
    public PdfLimits {
        if (maxPages < 1) {
            throw new IllegalArgumentException("app.pdf.max-pages must be >= 1, got " + maxPages);
        } else if (maxTextChars < 1) {
            throw new IllegalArgumentException("app.pdf.max-text-chars must be >= 1, got " + maxTextChars);
        } else if (maxExtractionTime.isNegative() || maxExtractionTime.isZero()) {
            throw new IllegalArgumentException("app.pdf.max-extraction-time must be a positive duration");
        }
    }

    /** Limits with all default values. */
    public static PdfLimits defaults() {
        return new PdfLimits(500, 2_000_000, Duration.ofSeconds(60));
    }
}
