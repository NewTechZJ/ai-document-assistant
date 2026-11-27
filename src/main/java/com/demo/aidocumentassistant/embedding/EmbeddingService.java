/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.embedding;

import com.demo.aidocumentassistant.common.error.AiCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The only class that talks to the Spring AI {@link EmbeddingModel}
 * (Ollama nomic-embed-text in "local", Bedrock Titan Text Embeddings V2 in "aws").
 *
 * <p>Adds the model's task prefix, checks every vector's dimension, wraps provider failures in
 * {@link AiCallException} (502), and logs texts / chars / input tokens / ms for every call. Text is never logged.
 */
public class EmbeddingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;

    /**
     * Result of embedding several texts.
     *
     * @param vectors     one vector per input text, same order
     * @param inputTokens tokens billed by the provider, summed over all calls (0 if the provider doesn't say)
     * @param inputChars  characters sent, including prefixes
     */
    public record Embeddings(List<float[]> vectors, int inputTokens, long inputChars) {
    }

    public EmbeddingService(EmbeddingModel embeddingModel, EmbeddingProperties properties) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Embeds a user question (with the query prefix).
     *
     * @throws AiCallException if the embedding call fails or returns the wrong number of vectors
     */
    public float[] embedQuery(String question) {
        Embeddings result = embedBatch(List.of(withPrefix(properties.queryPrefix(), question)), "query");
        return result.vectors().getFirst();
    }

    /**
     * Embeds chunk texts (with the document prefix), in batches of {@code app.embedding.batch-size}.
     *
     * @throws AiCallException if an embedding call fails or returns the wrong number of vectors
     */
    public Embeddings embedDocuments(List<String> texts) {
        if (texts.isEmpty()) {
            return new Embeddings(List.of(), 0, 0);
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        int tokens = 0;
        long chars = 0;
        for (int from = 0; from < texts.size(); from += properties.batchSize()) {
            List<String> batch = texts.subList(from, Math.min(from + properties.batchSize(), texts.size())).stream()
                    .map(text -> withPrefix(properties.documentPrefix(), text))
                    .toList();
            Embeddings part = embedBatch(batch, "document");
            vectors.addAll(part.vectors());
            tokens += part.inputTokens();
            chars += part.inputChars();
        }
        return new Embeddings(List.copyOf(vectors), tokens, chars);
    }

    /** Expected vector dimension for the active embedding model. */
    public int dimensions() {
        return properties.dimensions();
    }

    private Embeddings embedBatch(List<String> inputs, String kind) {
        long startNanos = System.nanoTime();
        long chars = inputs.stream().mapToLong(String::length).sum();

        EmbeddingResponse response = callModel(inputs, kind, chars);
        List<float[]> vectors = vectorsInInputOrder(response, inputs.size());
        int tokens = inputTokens(response);
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

        LOGGER.info("embed kind={} texts={} chars={} inTokens={} dims={} ms={}",
                kind, inputs.size(), chars, tokens, vectors.getFirst().length, latencyMs);
        return new Embeddings(vectors, tokens, chars);
    }

    /** Only the provider call is wrapped, so only real AI failures become a 502. */
    private EmbeddingResponse callModel(List<String> inputs, String kind, long chars) {
        try {
            return embeddingModel.call(new EmbeddingRequest(inputs, null));
        } catch (RuntimeException e) {
            LOGGER.error("Embedding call failed kind={} texts={} chars={}", kind, inputs.size(), chars, e);
            throw new AiCallException("The embedding model call failed.", e);
        }
    }

    /**
     * Checks count and dimension. Spring AI's Titan model silently skips an input whose embedding comes back empty,
     * which would shift every later vector onto the wrong chunk; the count check turns that into an error.
     */
    private List<float[]> vectorsInInputOrder(EmbeddingResponse response, int expectedCount) {
        if (response == null || response.getResults() == null || response.getResults().size() != expectedCount) {
            int got = response == null || response.getResults() == null ? 0 : response.getResults().size();
            throw new AiCallException("The embedding model returned " + got + " vectors for "
                    + expectedCount + " texts.", null);
        }
        List<float[]> vectors = response.getResults().stream()
                .sorted(Comparator.comparing(Embedding::getIndex, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(Embedding::getOutput)
                .toList();
        for (float[] vector : vectors) {
            if (vector == null || vector.length != properties.dimensions()) {
                int got = vector == null ? 0 : vector.length;
                throw new IllegalStateException("The embedding model returned a " + got + "-dimension vector but "
                        + "app.embedding.dimensions=" + properties.dimensions()
                        + ". Check the embedding model configured for this profile.");
            }
        }
        return vectors;
    }

    private static int inputTokens(EmbeddingResponse response) {
        if (response.getMetadata() == null) {
            return 0;
        }
        Usage usage = response.getMetadata().getUsage();
        Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        return promptTokens == null ? 0 : promptTokens;
    }

    private static String withPrefix(String prefix, String text) {
        return prefix.isEmpty() ? text : prefix + " " + text;
    }
}
