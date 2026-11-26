/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.ingestion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the ingestion package: registers the properties and creates the beans.
 * Keeping the wiring here means PdfTextExtractor and Chunker can still be created with plain {@code new}.
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
