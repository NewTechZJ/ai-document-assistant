package com.demo.aidocumentassistant.ingestion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The only Spring-aware class in this package: it turns the plain-Java classes into beans.
 * Keeping the wiring here means PdfTextExtractor and Chunker stay usable with plain `new`.
 */
@Configuration
@EnableConfigurationProperties({ChunkingProperties.class, PdfLimits.class})
public class IngestionConfig {

    @Bean
    PdfTextExtractor pdfTextExtractor(PdfLimits limits) {
        return new PdfTextExtractor(limits);
    }

    @Bean
    Chunker chunker(ChunkingProperties properties) {
        return new Chunker(properties);
    }
}
