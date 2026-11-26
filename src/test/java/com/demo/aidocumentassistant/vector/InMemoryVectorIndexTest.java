package com.demo.aidocumentassistant.vector;

import com.demo.aidocumentassistant.ingestion.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryVectorIndexTest {

    private InMemoryVectorIndex index;

    @BeforeEach
    void setUp() {
        index = new InMemoryVectorIndex();
    }

    @Test
    void ranksByCosineBestFirst() {
        index.upsert(
                List.of(chunk("doc", 0, 1), chunk("doc", 1, 2), chunk("doc", 2, 3)),
                List.of(new float[]{0, 1}, new float[]{1, 0}, new float[]{1, 1}));

        List<SearchHit> hits = index.search(new float[]{1, 0}, 3, "doc");

        assertEquals(List.of(1, 2, 0), indexes(hits));
        assertEquals(1.0, hits.get(0).score(), 1e-6);
        assertEquals(1 / Math.sqrt(2), hits.get(1).score(), 1e-6);
        assertEquals(0.0, hits.get(2).score(), 1e-6);
    }

    @Test
    void returnsAtMostTopK() {
        index.upsert(
                List.of(chunk("doc", 0, 1), chunk("doc", 1, 1), chunk("doc", 2, 1), chunk("doc", 3, 1)),
                List.of(new float[]{1, 0}, new float[]{0.9f, 0.1f}, new float[]{0.5f, 0.5f}, new float[]{0, 1}));

        List<SearchHit> hits = index.search(new float[]{1, 0}, 2, "doc");

        assertEquals(List.of(0, 1), indexes(hits));
    }

    @Test
    void topKMatchesAFullSortOnRandomData() {
        Random random = new Random(42);
        int n = 300;
        List<Chunk> chunks = new java.util.ArrayList<>();
        List<float[]> vectors = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            chunks.add(chunk("doc", i, i / 10 + 1));
            vectors.add(randomVector(random, 16));
        }
        index.upsert(chunks, vectors);
        float[] query = randomVector(random, 16);

        List<SearchHit> hits = index.search(query, 7, "doc");

        List<Integer> expected = java.util.stream.IntStream.range(0, n).boxed()
                .sorted((a, b) -> Double.compare(VectorMath.cosine(query, vectors.get(b)), VectorMath.cosine(query, vectors.get(a))))
                .limit(7).toList();
        assertEquals(expected, indexes(hits));
        for (int i = 1; i < hits.size(); i++) {
            assertTrue(hits.get(i - 1).score() >= hits.get(i).score());
        }
    }

    @Test
    void searchOnlyLooksInsideTheRequestedDocument() {
        index.upsert(
                List.of(chunk("a", 0, 1), chunk("b", 0, 1)),
                List.of(new float[]{0, 1}, new float[]{1, 0}));

        List<SearchHit> hits = index.search(new float[]{1, 0}, 5, "a");

        assertEquals(1, hits.size());
        assertEquals("a", hits.getFirst().chunk().docId());
    }

    @Test
    void nullDocIdSearchesAllDocuments() {
        index.upsert(
                List.of(chunk("a", 0, 1), chunk("b", 0, 1)),
                List.of(new float[]{0, 1}, new float[]{1, 0}));

        List<SearchHit> hits = index.search(new float[]{1, 0}, 5, null);

        assertEquals(List.of("b", "a"), hits.stream().map(h -> h.chunk().docId()).toList());
    }

    @Test
    void tiesGoToTheEarlierChunk() {
        index.upsert(
                List.of(chunk("doc", 2, 1), chunk("doc", 0, 1), chunk("doc", 1, 1)),
                List.of(new float[]{1, 0}, new float[]{1, 0}, new float[]{1, 0}));

        assertEquals(List.of(0, 1, 2), indexes(index.search(new float[]{1, 0}, 3, "doc")));
    }

    @Test
    void upsertReplacesTheSameChunk() {
        index.upsert(List.of(chunk("doc", 0, 1)), List.of(new float[]{0, 1}));
        index.upsert(List.of(chunk("doc", 0, 1)), List.of(new float[]{1, 0}));

        assertEquals(1, index.size("doc"));
        assertEquals(1.0, index.search(new float[]{1, 0}, 1, "doc").getFirst().score(), 1e-6);
    }

    @Test
    void deleteByDocRemovesOnlyThatDocument() {
        index.upsert(
                List.of(chunk("a", 0, 1), chunk("a", 1, 1), chunk("b", 0, 1)),
                List.of(new float[]{1, 0}, new float[]{1, 0}, new float[]{1, 0}));

        index.deleteByDoc("a");
        index.deleteByDoc("never-existed");

        assertEquals(0, index.size("a"));
        assertTrue(index.search(new float[]{1, 0}, 5, "a").isEmpty());
        assertEquals(1, index.search(new float[]{1, 0}, 5, "b").size());
    }

    @Test
    void emptyIndexReturnsNoHits() {
        assertTrue(index.search(new float[]{1, 0}, 5, "doc").isEmpty());
    }

    @Test
    void storedVectorsAreCopiedSoCallersCannotChangeThem() {
        float[] vector = {1, 0};
        index.upsert(List.of(chunk("doc", 0, 1)), List.of(vector));
        vector[0] = 0;
        vector[1] = 1;

        assertEquals(1.0, index.search(new float[]{1, 0}, 1, "doc").getFirst().score(), 1e-6);
    }

    @Test
    void mismatchedListSizesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> index.upsert(List.of(chunk("doc", 0, 1)), List.of()));
    }

    @Test
    void vectorsOfAnotherDimensionAreRejected() {
        index.upsert(List.of(chunk("doc", 0, 1)), List.of(new float[]{1, 0, 0}));

        var upsert = assertThrows(IllegalArgumentException.class,
                () -> index.upsert(List.of(chunk("doc", 1, 1)), List.of(new float[]{1, 0})));
        assertTrue(upsert.getMessage().contains("must not be mixed"));
        assertThrows(IllegalArgumentException.class, () -> index.search(new float[]{1, 0}, 1, "doc"));
    }

    @Test
    void aBadVectorLeavesTheIndexUnchanged() {
        index.upsert(List.of(chunk("doc", 0, 1)), List.of(new float[]{1, 0}));

        assertThrows(IllegalArgumentException.class, () -> index.upsert(
                List.of(chunk("doc", 1, 1), chunk("doc", 2, 1)),
                List.of(new float[]{1, 0}, new float[]{1, 0, 0})));

        assertEquals(1, index.size("doc"));
    }

    @Test
    void topKBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> index.search(new float[]{1, 0}, 0, "doc"));
    }

    private static Chunk chunk(String docId, int index, int page) {
        return new Chunk(docId, index, page, "text " + index);
    }

    private static List<Integer> indexes(List<SearchHit> hits) {
        return hits.stream().map(hit -> hit.chunk().index()).toList();
    }

    private static float[] randomVector(Random random, int dims) {
        float[] v = new float[dims];
        for (int i = 0; i < dims; i++) {
            v[i] = (float) random.nextGaussian();
        }
        return v;
    }
}
