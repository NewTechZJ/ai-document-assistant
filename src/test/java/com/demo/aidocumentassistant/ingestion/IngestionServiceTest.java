package com.demo.aidocumentassistant.ingestion;

import com.demo.aidocumentassistant.common.error.AiCallException;
import com.demo.aidocumentassistant.common.error.NotAPdfException;
import com.demo.aidocumentassistant.common.error.PdfTooLargeException;
import com.demo.aidocumentassistant.document.Document;
import com.demo.aidocumentassistant.document.DocumentStatus;
import com.demo.aidocumentassistant.document.InMemoryDocumentRepository;
import com.demo.aidocumentassistant.embedding.EmbeddingService;
import com.demo.aidocumentassistant.embedding.EmbeddingService.Embeddings;
import com.demo.aidocumentassistant.vector.InMemoryVectorIndex;
import com.demo.aidocumentassistant.vector.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Real extractor, chunker, index and repository; only EmbeddingService is mocked (no Ollama/Bedrock). */
class IngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    private InMemoryDocumentRepository documents;
    private InMemoryVectorIndex index;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        documents = new InMemoryDocumentRepository();
        index = new InMemoryVectorIndex();
        embeddingService = mock(EmbeddingService.class);
        // One 3-dim vector per text; the first value is the text's position.
        when(embeddingService.embedDocuments(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<float[]> vectors = new java.util.ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                vectors.add(new float[]{i + 1, 1, 0});
            }
            return new Embeddings(vectors, 11 * texts.size(), texts.stream().mapToLong(String::length).sum());
        });
    }

    @Test
    void ingestsAPdfAndMarksItReady() {
        Document result = service(500).ingest("report.pdf", TestPdfs.withPages("First page text.", "", "Third page text."));

        assertEquals(DocumentStatus.READY, result.status());
        assertEquals("report.pdf", result.fileName());
        assertEquals(3, result.pageCount());
        assertEquals(2, result.chunkCount());                     // the blank page gives no chunk
        assertEquals(NOW, result.createdAt());
        assertNull(result.error());
        assertEquals(result, documents.findById(result.docId()).orElseThrow());
        assertEquals(2, index.size(result.docId()));

        List<SearchHit> hits = index.search(new float[]{1, 1, 0}, 5, result.docId());
        assertEquals(List.of(1, 3), hits.stream().map(SearchHit::pageNumber).sorted().toList());
    }

    @Test
    void everyUploadGetsItsOwnDocId() {
        IngestionService service = service(500);
        Document a = service.ingest("a.pdf", TestPdfs.withPages("Alpha."));
        Document b = service.ingest("b.pdf", TestPdfs.withPages("Beta."));

        assertTrue(!a.docId().equals(b.docId()));
        assertEquals(2, documents.findAll().size());
    }

    @Test
    void notAPdfIsRecordedAsFailedAndRethrown() {
        IngestionService service = service(500);

        assertThrows(NotAPdfException.class,
                () -> service.ingest("notes.txt", "hello".getBytes(StandardCharsets.UTF_8)));

        Document failed = documents.findAll().getFirst();
        assertEquals(DocumentStatus.FAILED, failed.status());
        assertTrue(failed.error().contains("PDF"));
        verifyNoInteractions(embeddingService);
    }

    @Test
    void tooManyChunksFailsBeforeAnyEmbeddingCall() {
        IngestionService service = service(1);

        assertThrows(PdfTooLargeException.class,
                () -> service.ingest("big.pdf", TestPdfs.withPages("Page one.", "Page two.")));

        assertEquals(DocumentStatus.FAILED, documents.findAll().getFirst().status());
        verifyNoInteractions(embeddingService);
    }

    @Test
    void embeddingFailureMarksFailedWithAGenericReasonAndLeavesNoVectors() {
        when(embeddingService.embedDocuments(anyList())).thenThrow(new AiCallException("boom arn:aws:...", null));
        IngestionService service = service(500);

        assertThrows(AiCallException.class, () -> service.ingest("r.pdf", TestPdfs.withPages("Text.")));

        Document failed = documents.findAll().getFirst();
        assertEquals(DocumentStatus.FAILED, failed.status());
        assertEquals("The embedding model is unavailable. Try again.", failed.error());
        assertEquals(0, index.size(failed.docId()));
    }

    @Test
    void maxChunksBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service(0));
    }

    private IngestionService service(int maxChunks) {
        return new IngestionService(new PdfTextExtractor(), new Chunker(ChunkingProperties.defaults()),
                embeddingService, index, documents, 2, maxChunks, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
