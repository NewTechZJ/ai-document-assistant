/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.document;

import com.demo.aidocumentassistant.ingestion.ChunkingProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response from {@code POST /api/v1/documents/preview}: how a PDF would be split into chunks.
 */
@Schema(description = "Chunking preview of a PDF; nothing is stored")
public record DocumentPreview(

        @Schema(description = "Temporary id for this preview", example = "preview-3f2a9c1e-7b4d-4e8a-9f10-2c6d5e8b1a77")
        String docId,

        @Schema(description = "Uploaded file name", example = "handbook.pdf")
        String fileName,

        @Schema(description = "File size in bytes", example = "482133")
        long fileSizeBytes,

        @Schema(description = "Total number of pages", example = "12")
        int pageCount,

        @Schema(description = "Pages with no extractable text", example = "1")
        int blankPageCount,

        @Schema(description = "Total number of chunks, even when the chunks list is truncated", example = "34")
        int chunkCount,

        @Schema(description = "Chunk size statistics over all chunks, even when the chunks list is truncated")
        ChunkStats chunkStats,

        @Schema(description = "Chunking settings used for this preview")
        ChunkingProperties settings,

        @Schema(description = "One entry per page, in page order")
        List<PagePreview> pages,

        @Schema(description = "Chunks in order, at most 200; see chunksTruncated")
        List<ChunkPreview> chunks,

        @Schema(description = "True when the chunks list holds fewer than chunkCount chunks", example = "false")
        boolean chunksTruncated,

        @Schema(description = "Time taken to extract and chunk the PDF, in milliseconds", example = "215")
        long latencyMs
) {

    /** Summary of one page. */
    @Schema(description = "Summary of one page")
    public record PagePreview(

            @Schema(description = "Page number, starting at 1", example = "1")
            int pageNumber,

            @Schema(description = "Characters of text on the page", example = "2140")
            int charCount,

            @Schema(description = "Chunks created from this page", example = "2")
            int chunkCount,

            @Schema(description = "Start of the page text, for a quick look", example = "Chapter 1. Introduction ...")
            String snippet) {
    }

    /** One chunk of text. */
    @Schema(description = "One chunk of text")
    public record ChunkPreview(

            @Schema(description = "Chunk position across the whole document, starting at 0", example = "0")
            int index,

            @Schema(description = "Page the chunk comes from", example = "1")
            int pageNumber,

            @Schema(description = "Chunk length in characters", example = "1785")
            int charCount,

            @Schema(description = "Chunk text: full text if includeChunkText=true", nullable = true)
            String text) {
    }

    /** Chunk size statistics. */
    @Schema(description = "Chunk size statistics, in characters")
    public record ChunkStats(

            @Schema(description = "Smallest chunk", example = "240")
            int minChars,

            @Schema(description = "Largest chunk", example = "2069")
            int maxChars,

            @Schema(description = "Average chunk size, rounded to the nearest whole number", example = "1650")
            int avgChars) {
    }
}