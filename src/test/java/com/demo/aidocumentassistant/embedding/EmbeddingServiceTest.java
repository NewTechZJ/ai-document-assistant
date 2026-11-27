package com.demo.aidocumentassistant.embedding;

import com.demo.aidocumentassistant.common.error.AiCallException;
import com.demo.aidocumentassistant.embedding.EmbeddingService.Embeddings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** EmbeddingModel is a Mockito mock: nothing here calls Ollama or Bedrock. */
@ExtendWith(OutputCaptureExtension.class)
class EmbeddingServiceTest {

    private static final int DIMS = 4;

    private EmbeddingModel model;

    @BeforeEach
    void setUp() {
        model = mock(EmbeddingModel.class);
        // Each input text gets a vector whose first value is its position in the request; 7 tokens per text.
        when(model.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            EmbeddingRequest request = invocation.getArgument(0);
            int n = request.getInstructions().size();
            return response(IntStream.range(0, n).mapToObj(i -> vector(i, DIMS)).toList(), 7 * n);
        });
    }

    @Test
    void embedQueryAddsTheQueryPrefixAndReturnsTheVector() {
        var service = new EmbeddingService(model, new EmbeddingProperties(DIMS, "search_document:", "search_query:", 16));

        float[] vector = service.embedQuery("What is the refund window?");

        assertEquals(DIMS, vector.length);
        assertEquals(List.of("search_query: What is the refund window?"), sentTexts().getFirst());
    }

    @Test
    void noPrefixSendsTheTextUnchanged() {
        var service = new EmbeddingService(model, new EmbeddingProperties(DIMS, "", null, 16));

        service.embedQuery("hello");

        assertEquals(List.of("hello"), sentTexts().getFirst());
    }

    @Test
    void embedDocumentsBatchesKeepsOrderAndSumsTokens() {
        var service = new EmbeddingService(model, new EmbeddingProperties(DIMS, "search_document:", "", 2));

        Embeddings result = service.embedDocuments(List.of("a", "b", "c", "d", "e"));

        verify(model, times(3)).call(any(EmbeddingRequest.class));                 // 2 + 2 + 1
        assertEquals(5, result.vectors().size());
        assertEquals(List.of(0f, 1f, 0f, 1f, 0f), result.vectors().stream().map(v -> v[0]).toList());
        assertEquals(35, result.inputTokens());
        assertEquals(5L * ("search_document: ".length() + 1), result.inputChars());
        assertEquals(List.of("search_document: a", "search_document: b"), sentTexts().getFirst());
    }

    @Test
    void vectorsAreSortedByTheirResponseIndex() {
        when(model.call(any(EmbeddingRequest.class))).thenReturn(new EmbeddingResponse(List.of(
                new Embedding(vector(1, DIMS), 1),
                new Embedding(vector(0, DIMS), 0)), metadata(0)));
        var service = new EmbeddingService(model, new EmbeddingProperties(DIMS, "", "", 16));

        Embeddings result = service.embedDocuments(List.of("first", "second"));

        assertEquals(0f, result.vectors().get(0)[0]);
        assertEquals(1f, result.vectors().get(1)[0]);
    }

    @Test
    void emptyInputMakesNoCall() {
        var service = new EmbeddingService(model, new EmbeddingProperties(DIMS, "", "", 16));

        Embeddings result = service.embedDocuments(List.of());

        assertTrue(result.vectors().isEmpty());
        verify(model, times(0)).call(any(EmbeddingRequest.class));
    }

    @Test
    void wrongDimensionFailsLoudly() {
        var service = new EmbeddingService(model, new EmbeddingProperties(1024, "", "", 16));

        var e = assertThrows(IllegalStateException.class, () -> service.embedQuery("q"));
        assertTrue(e.getMessage().contains("app.embedding.dimensions=1024"));
    }

    @Test
    void fewerVectorsThanTextsIsAnError() {
        // Spring AI's Titan model skips an input whose embedding comes back empty; that must not shift vectors.
        when(model.call(any(EmbeddingRequest.class))).thenReturn(response(List.of(vector(0, DIMS)), 3));
        var service = new EmbeddingService(model, new EmbeddingProperties(DIMS, "", "", 16));

        assertThrows(AiCallException.class, () -> service.embedDocuments(List.of("a", "b")));
    }

    @Test
    void providerFailureBecomesAiCallException() {
        var cause = new RuntimeException("ThrottlingException");
        when(model.call(any(EmbeddingRequest.class))).thenThrow(cause);
        var service = new EmbeddingService(model, new EmbeddingProperties(DIMS, "", "", 16));

        var e = assertThrows(AiCallException.class, () -> service.embedQuery("q"));
        assertSame(cause, e.getCause());
    }

    @Test
    void missingUsageCountsAsZeroTokens() {
        when(model.call(any(EmbeddingRequest.class))).thenReturn(
                new EmbeddingResponse(List.of(new Embedding(vector(0, DIMS), 0))));
        var service = new EmbeddingService(model, new EmbeddingProperties(DIMS, "", "", 16));

        assertEquals(0, service.embedDocuments(List.of("a")).inputTokens());
    }

    @Test
    void logsCountsAndTokensButNeverTheText(CapturedOutput output) {
        var service = new EmbeddingService(model, new EmbeddingProperties(DIMS, "", "", 16));

        service.embedQuery("my secret question");

        assertTrue(output.getOut().contains("embed kind=query texts=1 chars=18 inTokens=7 dims=4"));
        assertFalse(output.getOut().contains("my secret question"));
    }

    @Test
    void invalidPropertiesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingProperties(0, "", "", 16));
        assertThrows(IllegalArgumentException.class, () -> new EmbeddingProperties(DIMS, "", "", 0));
    }

    private List<List<String>> sentTexts() {
        ArgumentCaptor<EmbeddingRequest> captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(model, org.mockito.Mockito.atLeastOnce()).call(captor.capture());
        List<List<String>> texts = new ArrayList<>();
        captor.getAllValues().forEach(request -> texts.add(request.getInstructions()));
        return texts;
    }

    private static EmbeddingResponse response(List<float[]> vectors, int tokens) {
        List<Embedding> embeddings = IntStream.range(0, vectors.size())
                .mapToObj(i -> new Embedding(vectors.get(i), i))
                .toList();
        return new EmbeddingResponse(embeddings, metadata(tokens));
    }

    private static EmbeddingResponseMetadata metadata(int tokens) {
        return new EmbeddingResponseMetadata("test-model", new DefaultUsage(tokens, 0));
    }

    /** First value = marker, rest = 1, so every vector is non-zero. */
    private static float[] vector(int marker, int dims) {
        float[] v = new float[dims];
        java.util.Arrays.fill(v, 1f);
        v[0] = marker;
        return v;
    }
}
