/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link EmbeddingService} to whichever EmbeddingModel the active profile enabled:
 * spring.ai.model.embedding=ollama (local) or bedrock-titan (aws). Exactly one is ever active.
 */
@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfig {

    @Bean
    EmbeddingService embeddingService(EmbeddingModel embeddingModel, EmbeddingProperties properties) {
        return new EmbeddingService(embeddingModel, properties);
    }
}
