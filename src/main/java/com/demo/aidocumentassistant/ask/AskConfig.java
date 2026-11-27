/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.ask;

import com.demo.aidocumentassistant.document.DocumentRepository;
import com.demo.aidocumentassistant.embedding.EmbeddingService;
import com.demo.aidocumentassistant.vector.VectorIndex;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the document question-answering feature.
 */
@Configuration
@EnableConfigurationProperties(AskProperties.class)
public class AskConfig {

    @Bean
    PromptBuilder promptBuilder() {
        return new PromptBuilder();
    }

    /** ChatClient.Builder is a prototype bean, so this builder is not shared with ChatService. */
    @Bean
    AskService askService(DocumentRepository documentRepository, EmbeddingService embeddingService,
                          VectorIndex vectorIndex, PromptBuilder promptBuilder,
                          ChatClient.Builder chatClientBuilder, AskProperties properties) {
        return new AskService(documentRepository, embeddingService, vectorIndex, promptBuilder,
                chatClientBuilder, properties);
    }
}
