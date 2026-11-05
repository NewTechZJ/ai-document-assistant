package com.demo.aidocumentassistant.document;

import com.demo.aidocumentassistant.ingestion.ChunkingProperties;

import java.util.List;

public record DocumentPreview(
        String docId,
        String fileName,
        long fileSizeBytes,
        int pageCount,
        int blankPageCount,
        int chunkCount,              // always the full count
        ChunkStats chunkStats,       // always over all chunks
        ChunkingProperties settings,
        List<PagePreview> pages,
        List<ChunkPreview> chunks,
        boolean chunksTruncated,     // true when chunks.size() < chunkCount
        long latencyMs
) {
    public record PagePreview(int pageNumber, int charCount, int chunkCount, String snippet) {
    }

    public record ChunkPreview(int index, int pageNumber, int charCount, String text) {
    }

    public record ChunkStats(int minChars, int maxChars, int avgChars) {
    }
}
