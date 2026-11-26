/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.vector;

import com.demo.aidocumentassistant.ingestion.Chunk;

import java.util.List;

/**
 * Stores chunk vectors and finds the ones most similar to a query vector.
 */
public interface VectorIndex {

    /**
     * Adds or replaces the vectors of these chunks. A chunk is identified by (docId, index),
     * so upserting the same chunk again overwrites it.
     *
     * @param chunks  the chunks, in any order, possibly from several documents
     * @param vectors one vector per chunk, same order and size as {@code chunks}
     * @throws IllegalArgumentException when the lists differ in size or a vector has the wrong dimension
     */
    void upsert(List<Chunk> chunks, List<float[]> vectors);

    /**
     * Returns the {@code topK} chunks most similar to {@code query}, best first.
     *
     * @param query the query vector
     * @param topK  maximum number of hits to return; must be at least 1
     * @param docId only search this document; {@code null} searches all documents
     * @return at most topK hits, sorted by score descending (ties: lower chunk index first); empty if nothing stored
     * @throws IllegalArgumentException when topK is below 1 or the query has the wrong dimension
     */
    List<SearchHit> search(float[] query, int topK, String docId);

    /** Removes every vector of the document. Does nothing if the document has none. */
    void deleteByDoc(String docId);
}
