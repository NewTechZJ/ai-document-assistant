/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.ask;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retrieval settings, under "app.ask".
 *
 * @param topK         chunks retrieved per question (5 x ~2,000 chars ~ 2,500 input tokens)
 * @param minScore     cosine similarity a chunk needs to be sent to the model. Model-specific (set per profile):
 *                     calibrate it from the "topScore" in the ask logs. If no chunk passes, the service answers
 *                     "I couldn't find that in the document." without calling the chat model (costs nothing).
 * @param snippetChars length of the snippet returned with each citation
 */
@ConfigurationProperties("app.ask")
public record AskProperties(
        @DefaultValue("5") int topK,
        @DefaultValue("0.2") double minScore,
        @DefaultValue("200") int snippetChars
) {
    public AskProperties {
        if (topK < 1 || topK > 20) {
            throw new IllegalArgumentException("app.ask.top-k must be between 1 and 20, got " + topK);
        }
        if (minScore < -1 || minScore > 1) {
            throw new IllegalArgumentException("app.ask.min-score must be between -1 and 1, got " + minScore);
        }
        if (snippetChars < 20) {
            throw new IllegalArgumentException("app.ask.snippet-chars must be >= 20, got " + snippetChars);
        }
    }
}
