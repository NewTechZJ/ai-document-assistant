/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.ingestion;

import com.demo.aidocumentassistant.common.error.AiCallException;
import com.demo.aidocumentassistant.common.error.NoExtractableTextException;
import com.demo.aidocumentassistant.common.error.PdfExtractionException;
import com.demo.aidocumentassistant.common.error.PdfTooLargeException;
import com.demo.aidocumentassistant.common.error.ServerBusyException;
import com.demo.aidocumentassistant.document.Document;
import com.demo.aidocumentassistant.document.DocumentRepository;
import com.demo.aidocumentassistant.embedding.EmbeddingService;
import com.demo.aidocumentassistant.embedding.EmbeddingService.Embeddings;
import com.demo.aidocumentassistant.vector.VectorIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * Processes uploaded PDFs: extracts text, chunks it, embeds chunks, and stores vectors.
 *
 * <p>Limits concurrent ingestion to avoid overloading the embedding model. On failure,
 * saves a FAILED document record, removes any vectors already written, and rethrows
 * the exception for ApiExceptionHandler to map to an HTTP response.
 *
 * <p>Logs only metrics (bytes, pages, chunks, tokens, latency), never the actual text content.
 */
@Service
public class IngestionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionService.class);

    private final PdfTextExtractor pdfTextExtractor;
    private final Chunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorIndex vectorIndex;
    private final DocumentRepository documentRepository;
    private final Semaphore ingestionSlots;
    private final int maxChunks;
    private final Clock clock;

    @Autowired
    public IngestionService(
            PdfTextExtractor pdfTextExtractor,
            Chunker chunker,
            EmbeddingService embeddingService,
            VectorIndex vectorIndex,
            DocumentRepository documentRepository,
            @Value("${app.pdf.max-concurrent:2}") int maxConcurrent,
            @Value("${app.ingest.max-chunks:500}") int maxChunks) {
        this(pdfTextExtractor, chunker, embeddingService, vectorIndex, documentRepository,
                maxConcurrent, maxChunks, Clock.systemUTC());
    }

    /**
     * Constructor for testing, allowing injection of a custom Clock for time control.
     */
    IngestionService(
            PdfTextExtractor pdfTextExtractor,
            Chunker chunker,
            EmbeddingService embeddingService,
            VectorIndex vectorIndex,
            DocumentRepository documentRepository,
            int maxConcurrent,
            int maxChunks,
            Clock clock) {
        if (maxChunks < 1) {
            throw new IllegalArgumentException("app.ingest.max-chunks must be >= 1, got " + maxChunks);
        }
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("app.pdf.max-concurrent must be >= 1, got " + maxConcurrent);
        }
        this.pdfTextExtractor = pdfTextExtractor;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
        this.documentRepository = documentRepository;
        this.ingestionSlots = new Semaphore(maxConcurrent);
        this.maxChunks = maxChunks;
        this.clock = clock;
    }

    /**
     * Ingests one PDF: extracts text, chunks it, embeds chunks, and returns a READY document record.
     *
     * <p>On any failure, saves a FAILED record with a user-friendly error message, removes any
     * vectors already written to the index, and rethrows the exception for ApiExceptionHandler.
     *
     * @param fileName the original file name (for display only)
     * @param pdfBytes the PDF content as bytes
     * @return a Document record with status READY, page count, and chunk count
     * @throws ServerBusyException if too many documents are already being processed
     * @throws RuntimeException any error from PDF extraction, chunking, or embedding (mapped by ApiExceptionHandler)
     */
    public Document ingest(String fileName, byte[] pdfBytes) {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(pdfBytes, "pdfBytes");
        if (!ingestionSlots.tryAcquire()) {
            throw new ServerBusyException("Too many documents are being processed. Try again in a few seconds.");
        }
        try {
            return doIngest(fileName, pdfBytes);
        } finally {
            ingestionSlots.release();
        }
    }

    /** Performs the actual ingestion pipeline with error recovery. */
    private Document doIngest(String fileName, byte[] pdfBytes) {
        long startNanos = System.nanoTime();
        Document document = Document.processing(UUID.randomUUID().toString(), fileName, pdfBytes.length, clock.instant());
        String docId = document.docId();
        documentRepository.save(document);

        try {
            List<PageText> pages = pdfTextExtractor.extract(pdfBytes);
            List<Chunk> chunks = chunker.chunk(docId, pages);
            if (chunks.isEmpty()) {
                throw new NoExtractableTextException("The PDF produced no text chunks.");
            }
            // Cost guard: one embedding call per chunk on Bedrock Titan, so cap the count before calling.
            if (chunks.size() > maxChunks) {
                throw new PdfTooLargeException("The PDF produced " + chunks.size()
                        + " chunks; the limit is " + maxChunks + " (app.ingest.max-chunks).");
            }

            // Call embedding service and store vectors; on failure, the catch block will clean up.
            Embeddings embeddings = embeddingService.embedDocuments(chunks.stream().map(Chunk::text).toList());
            vectorIndex.upsert(chunks, embeddings.vectors());

            Document ready = document.ready(pages.size(), chunks.size());
            documentRepository.save(ready);
            LOGGER.info("ingest docId={} bytes={} pages={} chunks={} embedChars={} embedInTokens={} ms={}",
                    docId, pdfBytes.length, pages.size(), chunks.size(),
                    embeddings.inputChars(), embeddings.inputTokens(), (System.nanoTime() - startNanos) / 1_000_000);
            return ready;

        } catch (RuntimeException e) {
            // Clean up any vectors already written, save FAILED record, and rethrow.
            vectorIndex.deleteByDoc(docId);
            documentRepository.save(document.failed(clientSafeReason(e)));
            LOGGER.warn("ingest failed docId={} bytes={} reason={}", docId, pdfBytes.length, e.toString());
            throw e;
        }
    }

    /**
     * Extracts a user-friendly error message from a caught exception.
     *
     * <p>PDF-related errors are shown verbatim (the user can fix them). AI/embedding errors
     * and anything else return a generic message to avoid exposing internal details.
     *
     * @param e the exception caught during ingestion
     * @return a message safe to send to the client
     */
    private static String clientSafeReason(RuntimeException e) {
        return switch (e) {
            case PdfExtractionException pdf -> pdf.getMessage();
            case AiCallException ai -> "The embedding model is unavailable. Try again.";
            default -> "Processing failed.";
        };
    }
}
