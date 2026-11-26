package com.demo.aidocumentassistant.vector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorMathTest {

    private static final double EPS = 1e-9;

    @Test
    void identicalVectorsScoreOne() {
        float[] v = {0.3f, -1.2f, 4.0f};
        assertEquals(1.0, VectorMath.cosine(v, v), 1e-6);
    }

    @Test
    void orthogonalVectorsScoreZero() {
        assertEquals(0.0, VectorMath.cosine(new float[]{1, 0}, new float[]{0, 1}), EPS);
    }

    @Test
    void oppositeVectorsScoreMinusOne() {
        assertEquals(-1.0, VectorMath.cosine(new float[]{1, 2, 3}, new float[]{-1, -2, -3}), 1e-6);
    }

    @Test
    void lengthDoesNotMatterOnlyDirection() {
        float[] a = {1, 2, 3};
        float[] scaled = {10, 20, 30};
        assertEquals(1.0, VectorMath.cosine(a, scaled), 1e-6);
    }

    @Test
    void knownValue() {
        // (1,0) vs (1,1): cos 45° = 1/sqrt(2)
        assertEquals(1 / Math.sqrt(2), VectorMath.cosine(new float[]{1, 0}, new float[]{1, 1}), 1e-6);
    }

    @Test
    void zeroVectorScoresZeroInsteadOfNaN() {
        double score = VectorMath.cosine(new float[]{0, 0, 0}, new float[]{1, 2, 3});
        assertEquals(0.0, score, EPS);
    }

    @Test
    void differentDimensionsAreRejected() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> VectorMath.cosine(new float[]{1, 2}, new float[]{1, 2, 3}));
        assertTrue(e.getMessage().contains("2 vs 3"));
    }

    @Test
    void normIsEuclideanLength() {
        assertEquals(5.0, VectorMath.norm(new float[]{3, 4}), EPS);
    }

    @Test
    void cosineFromPartsIsClampedAgainstRounding() {
        assertEquals(1.0, VectorMath.cosineFromParts(1.0000001, 1, 1), EPS);
        assertEquals(-1.0, VectorMath.cosineFromParts(-1.0000001, 1, 1), EPS);
    }
}
