/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for {@link EmbeddingService}, under "app.embedding". Set per profile, because each profile uses a
 * different embedding model and the two produce vectors that must never be mixed.
 *
 * @param dimensions     the vector size the configured model must return (Titan V2: 1024, nomic-embed-text: 768).
 *                       Every response is checked against it, so a wrong model fails loudly instead of silently.
 * @param documentPrefix task prefix put before every chunk text ("" for none). nomic-embed-text expects
 *                       "search_document:"; Titan needs none. A space is added between prefix and text.
 * @param queryPrefix    task prefix put before every question ("" for none). nomic-embed-text expects "search_query:".
 * @param batchSize      texts per EmbeddingModel call. Ollama embeds a batch in one request; Titan has no batch API,
 *                       so Spring AI makes one Bedrock call per text anyway.
 */
@ConfigurationProperties("app.embedding")
public record EmbeddingProperties(
        @DefaultValue("1024") int dimensions,
        String documentPrefix,
        String queryPrefix,
        @DefaultValue("16") int batchSize
) {
    public EmbeddingProperties {
        if (dimensions < 1) {
            throw new IllegalArgumentException("app.embedding.dimensions must be >= 1, got " + dimensions);
        }
        if (batchSize < 1 || batchSize > 256) {
            throw new IllegalArgumentException("app.embedding.batch-size must be between 1 and 256, got " + batchSize);
        }
        documentPrefix = documentPrefix == null ? "" : documentPrefix.strip();
        queryPrefix = queryPrefix == null ? "" : queryPrefix.strip();
    }
}
