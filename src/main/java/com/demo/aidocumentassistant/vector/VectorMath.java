/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.vector;

/** Small vector helpers. Sums are done in double so 1024 float products don't lose precision. */
public final class VectorMath {

    private VectorMath() {
    }

    /**
     * Cosine similarity in [-1, 1]: 1 = same direction, 0 = unrelated, -1 = opposite.
     * Works for normalized and non-normalized vectors alike (it divides by both lengths).
     * Returns 0 when either vector has length 0, instead of NaN.
     */
    public static double cosine(float[] a, float[] b) {
        requireSameLength(a, b);
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return cosineFromParts(dot, Math.sqrt(normA), Math.sqrt(normB));
    }

    /** Euclidean length. The index stores it per vector so search only computes it once for the query. */
    public static double norm(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += (double) x * x;
        }
        return Math.sqrt(sum);
    }

    /** Dot product of two vectors of the same length. */
    public static double dot(float[] a, float[] b) {
        requireSameLength(a, b);
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
    }

    /** Cosine from a precomputed dot product and lengths; clamped to [-1, 1] against rounding error. */
    static double cosineFromParts(double dot, double normA, double normB) {
        if (normA == 0 || normB == 0) {
            return 0;
        }
        double cosine = dot / (normA * normB);
        return Math.max(-1.0, Math.min(1.0, cosine));
    }

    private static void requireSameLength(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vectors have different dimensions: " + a.length + " vs " + b.length);
        }
    }
}
