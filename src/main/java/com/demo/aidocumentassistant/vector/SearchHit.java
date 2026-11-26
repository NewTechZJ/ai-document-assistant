/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.vector;

import com.demo.aidocumentassistant.ingestion.Chunk;

import java.util.Objects;

/**
 * One search result.
 *
 * @param chunk the stored chunk (text, page number, index)
 * @param score cosine similarity to the query, in [-1, 1]; higher is more similar
 */
public record SearchHit(
        Chunk chunk,
        double score
) {
    public SearchHit {
        Objects.requireNonNull(chunk, "chunk");
    }

    /** Page the chunk came from, 1-based. */
    public int pageNumber() {
        return chunk.pageNumber();
    }
}
