/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.vector;

import com.demo.aidocumentassistant.ingestion.Chunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brute-force cosine search over vectors kept in memory. Fine for a few thousand chunks
 * (one 1024-dim dot product is ~1 µs), and lost on restart.
 *
 * <p>Thread-safe: each document's entries are an immutable snapshot that upsert/delete replace atomically,
 * so a search never sees a half-written document.
 *
 * <p>The first stored vector fixes the dimension for the life of this index; any vector or query of another
 * dimension is rejected. That is a guard against mixing embedding models (768-dim nomic-embed-text vs
 * 1024-dim Titan V2), not a full guarantee: two models with the same dimension would still pass.
 */
public class InMemoryVectorIndex implements VectorIndex {

    private static final int UNSET = -1;

    /** Ranks best first; ties go to the earlier chunk, so results are deterministic. */
    private static final Comparator<SearchHit> BEST_FIRST = Comparator
            .comparingDouble(SearchHit::score).reversed()
            .thenComparing(hit -> hit.chunk().docId())
            .thenComparingInt(hit -> hit.chunk().index());

    /** docId -> (chunkIndex -> entry), inner map is never modified after publication. */
    private final Map<String, Map<Integer, Entry>> documents = new ConcurrentHashMap<>();
    private final AtomicInteger dimension = new AtomicInteger(UNSET);

    private record Entry(Chunk chunk, float[] vector, double norm) {
    }

    @Override
    public void upsert(List<Chunk> chunks, List<float[]> vectors) {
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(vectors, "vectors");
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException(
                    "Got " + chunks.size() + " chunks but " + vectors.size() + " vectors");
        }

        // Validate everything first, so a bad vector halfway through doesn't leave a partial upsert.
        Map<String, List<Entry>> byDoc = new TreeMap<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = Objects.requireNonNull(chunks.get(i), "chunk " + i);
            float[] vector = Objects.requireNonNull(vectors.get(i), "vector " + i);
            requireDimension(vector, "vector for chunk " + chunk.index());
            float[] copy = vector.clone();   // callers can't change stored vectors afterwards
            byDoc.computeIfAbsent(chunk.docId(), id -> new ArrayList<>())
                    .add(new Entry(chunk, copy, VectorMath.norm(copy)));
        }

        byDoc.forEach((docId, entries) -> documents.compute(docId, (id, existing) -> {
            Map<Integer, Entry> updated = existing == null ? new TreeMap<>() : new TreeMap<>(existing);
            entries.forEach(entry -> updated.put(entry.chunk().index(), entry));
            return Map.copyOf(updated);
        }));
    }

    @Override
    public List<SearchHit> search(float[] query, int topK, String docId) {
        Objects.requireNonNull(query, "query");
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be >= 1, got " + topK);
        }
        if (dimension.get() == UNSET) {
            return List.of();                       // nothing stored yet
        }
        requireDimension(query, "query vector");
        double queryNorm = VectorMath.norm(query);

        // Keep only the best topK in a min-heap: O(n log k) instead of sorting all n.
        PriorityQueue<SearchHit> best = new PriorityQueue<>(topK + 1, BEST_FIRST.reversed());
        for (Entry entry : entriesFor(docId)) {
            double score = VectorMath.cosineFromParts(VectorMath.dot(query, entry.vector()), queryNorm, entry.norm());
            best.offer(new SearchHit(entry.chunk(), score));
            if (best.size() > topK) {
                best.poll();                        // drops the current worst
            }
        }
        List<SearchHit> hits = new ArrayList<>(best);
        hits.sort(BEST_FIRST);
        return List.copyOf(hits);
    }

    @Override
    public void deleteByDoc(String docId) {
        Objects.requireNonNull(docId, "docId");
        documents.remove(docId);
    }

    /** Number of stored vectors for the document (0 if unknown). For tests and logs. */
    public int size(String docId) {
        Map<Integer, Entry> entries = documents.get(docId);
        return entries == null ? 0 : entries.size();
    }

    private Collection<Entry> entriesFor(String docId) {
        if (docId != null) {
            Map<Integer, Entry> entries = documents.get(docId);
            return entries == null ? List.of() : entries.values();
        }
        List<Entry> all = new ArrayList<>();
        documents.values().forEach(entries -> all.addAll(entries.values()));
        return all;
    }

    private void requireDimension(float[] vector, String what) {
        if (vector.length == 0) {
            throw new IllegalArgumentException(what + " is empty");
        }
        dimension.compareAndSet(UNSET, vector.length);
        int expected = dimension.get();
        if (vector.length != expected) {
            throw new IllegalArgumentException(what + " has " + vector.length + " dimensions but this index holds "
                    + expected + "-dimension vectors. Vectors from different embedding models must not be mixed.");
        }
    }
}
