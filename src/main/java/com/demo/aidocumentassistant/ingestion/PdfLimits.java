package com.demo.aidocumentassistant.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.pdf")
public record PdfLimits(
        @DefaultValue("500") int maxPages,
        @DefaultValue("2000000") int maxTextChars,      // about 600-700 dense pages
        @DefaultValue("60s") Duration maxExtractionTime
) {
    public PdfLimits {
        if (maxPages < 1 || maxTextChars < 1 || maxExtractionTime.isNegative() || maxExtractionTime.isZero()) {
            throw new IllegalArgumentException("app.pdf.* limits must be positive");
        }
    }
    public static PdfLimits defaults() {
        return new PdfLimits(500, 2_000_000, Duration.ofSeconds(60));
    }
}
