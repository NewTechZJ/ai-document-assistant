package com.demo.aidocumentassistant.document;

import com.demo.aidocumentassistant.ingestion.ChunkingProperties;

import java.util.List;

public record DocumentPreview(
        String docId,
        String fileName,
        long fileSizeBytes,
        int pageCount,
        int blankPageCount,
        int chunkCount,
        ChunkStats chunkStats,
        ChunkingProperties settings,
        List<PagePreview> pages,
        List<ChunkPreview> chunks,
        long latencyMs
) {
    public record PagePreview(int pageNumber, int charCount, int chunkCount, String snippet) {
    }

    public record ChunkPreview(int index, int pageNumber, int charCount, String text) {
    }

    public record ChunkStats(int minChars, int maxChars, int avgChars) {
    }
}
