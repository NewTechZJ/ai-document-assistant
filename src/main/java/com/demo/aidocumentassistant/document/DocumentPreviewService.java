package com.demo.aidocumentassistant.document;

import com.demo.aidocumentassistant.common.error.ServerBusyException;
import com.demo.aidocumentassistant.document.DocumentPreview.ChunkPreview;
import com.demo.aidocumentassistant.document.DocumentPreview.ChunkStats;
import com.demo.aidocumentassistant.document.DocumentPreview.PagePreview;
import com.demo.aidocumentassistant.ingestion.Chunk;
import com.demo.aidocumentassistant.ingestion.Chunker;
import com.demo.aidocumentassistant.ingestion.ChunkingProperties;
import com.demo.aidocumentassistant.ingestion.PageText;
import com.demo.aidocumentassistant.ingestion.PdfTextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/** Runs extraction + chunking on an uploaded PDF and reports the result. Stores nothing. */
@Service
public class DocumentPreviewService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentPreviewService.class);
    private static final int SNIPPET_CODE_POINTS = 120;
    private static final int MAX_CHUNKS_IN_RESPONSE = 200;

    private final PdfTextExtractor pdfTextExtractor;
    private final Chunker chunker;
    private final ChunkingProperties chunkingProperties;
    private final Semaphore extractionSlots;

    public DocumentPreviewService(
            PdfTextExtractor pdfTextExtractor,
            Chunker chunker,
            ChunkingProperties chunkingProperties,
            @Value("${app.pdf.max-concurrent:2}") int maxConcurrent) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.chunker = chunker;
        this.chunkingProperties = chunkingProperties;
        this.extractionSlots = new Semaphore(maxConcurrent);
    }

    public DocumentPreview preview(String fileName, byte[] pdfBytes, boolean includeChunkText) {
        if (!extractionSlots.tryAcquire()) {
            throw new ServerBusyException("Too many documents are being processed. Try again in a few seconds.");
        }
        try {
            return doPreview(fileName, pdfBytes, includeChunkText);   // the current method body
        } finally {
            extractionSlots.release();
        }
    }

    private DocumentPreview doPreview(String fileName, byte[] pdfBytes, boolean includeChunkText) {
        long startNanos = System.nanoTime();
        var docId = "preview-" + UUID.randomUUID();

        List<PageText> pages = pdfTextExtractor.extract(pdfBytes);
        List<Chunk> chunks = chunker.chunk(docId, pages);

        Map<Integer, Long> chunkCountByPage = chunks.stream()
                .collect(Collectors.groupingBy(Chunk::pageNumber, Collectors.counting()));

        List<PagePreview> pagePreviews = pages.stream()
                .map(page -> new PagePreview(
                        page.pageNumber(),
                        page.text().length(),
                        chunkCountByPage.getOrDefault(page.pageNumber(), 0L).intValue(),
                        pageSnippet(page.text())))
                .toList();

        // Full text only on request, and never more than MAX_CHUNKS_IN_RESPONSE chunks, so the response stays small.
        List<ChunkPreview> chunkPreviews = chunks.stream()
                .limit(MAX_CHUNKS_IN_RESPONSE)
                .map(chunk -> new ChunkPreview(
                        chunk.index(),
                        chunk.pageNumber(),
                        chunk.charCount(),                                   // real length, even when text is a snippet
                        includeChunkText ? chunk.text() : pageSnippet(chunk.text())))
                .toList();
        boolean chunksTruncated = chunks.size() > MAX_CHUNKS_IN_RESPONSE;

        int blankPageCount = (int) pages.stream().filter(PageText::isBlank).count();
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

        LOGGER.info("preview docId={} bytes={} pages={} blankPages={} chunks={} fullText={} ms={}",
                docId, pdfBytes.length, pages.size(), blankPageCount, chunks.size(), includeChunkText, latencyMs);

        return new DocumentPreview(
                docId,
                fileName,
                pdfBytes.length,
                pages.size(),
                blankPageCount,
                chunks.size(),
                chunkSizeStats(chunks),          // stats use all chunks, not just the returned ones
                chunkingProperties,
                pagePreviews,
                chunkPreviews,
                chunksTruncated,
                latencyMs);
    }

    /** First ~120 characters, cut on a code point so an emoji is never split in half. */
    private static String pageSnippet(String text) {
        if (text.codePointCount(0, text.length()) <= SNIPPET_CODE_POINTS) {
            return text;
        }
        int end = text.offsetByCodePoints(0, SNIPPET_CODE_POINTS);
        return text.substring(0, end).stripTrailing() + "…";
    }

    /** Min / max / rounded average chunk length; all zeros when there are no chunks. */
    private static ChunkStats chunkSizeStats(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return new ChunkStats(0, 0, 0);
        }
        IntSummaryStatistics summary = chunks.stream().mapToInt(Chunk::charCount).summaryStatistics();
        return new ChunkStats(summary.getMin(), summary.getMax(), (int) Math.round(summary.getAverage()));
    }
}
