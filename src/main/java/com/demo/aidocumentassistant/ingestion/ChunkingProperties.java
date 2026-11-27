/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Chunking settings, under "app.chunking".
 *
 * @param targetChars  maximum characters per chunk
 * @param overlapChars characters repeated from the end of the previous chunk at the start of the next one
 * @param minChars     minimum characters of new text in the last chunk of a page; shorter text is merged
 *                     into the previous chunk
 */
@ConfigurationProperties("app.chunking")
public record ChunkingProperties(
        @DefaultValue("" + ChunkingProperties.DEFAULT_TARGET_CHARS) int targetChars,
        @DefaultValue("" + ChunkingProperties.DEFAULT_OVERLAP_CHARS) int overlapChars,
        @DefaultValue("" + ChunkingProperties.DEFAULT_MIN_CHARS) int minChars
) {
    public static final int DEFAULT_TARGET_CHARS = 1800;
    public static final int DEFAULT_OVERLAP_CHARS = 270;
    public static final int DEFAULT_MIN_CHARS = 200;

    /** Fails at startup with a clear message instead of producing strange chunks at runtime. */
    public ChunkingProperties {
        if (targetChars < 100) {
            throw new IllegalArgumentException("app.chunking.target-chars must be >= 100, got " + targetChars);
        }
        if (overlapChars < 0 || overlapChars > targetChars / 2) {
            throw new IllegalArgumentException(
                    "app.chunking.overlap-chars must be between 0 and target-chars/2, got " + overlapChars);
        }
        if (minChars < 0 || minChars >= targetChars) {
            throw new IllegalArgumentException(
                    "app.chunking.min-chars must be between 0 and target-chars, got " + minChars);
        }
    }

    /** Settings with all default values. */
    public static ChunkingProperties defaults() {
        return new ChunkingProperties(DEFAULT_TARGET_CHARS, DEFAULT_OVERLAP_CHARS, DEFAULT_MIN_CHARS);
    }
}
