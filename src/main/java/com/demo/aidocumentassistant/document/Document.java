/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.document;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;

/**
 * An uploaded document and its processing state.
 */
@Schema(description = "An uploaded document and its processing state")
public record Document(

        @Schema(description = "Unique document identifier", example = "3f2b8c1e-6a4d-4e9b-9d2a-1c7f5e8a0b34")
        String docId,

        @Schema(description = "Original file name", example = "annual-report-2025.pdf")
        String fileName,

        @Schema(description = "File size in bytes", example = "1048576")
        long fileSizeBytes,

        @Schema(description = "Processing status: PROCESSING, READY or FAILED", example = "READY")
        DocumentStatus status,

        @Schema(description = "Number of pages; 0 until processing completes", example = "42")
        int pageCount,

        @Schema(description = "Number of text chunks indexed; 0 until processing completes", example = "118")
        int chunkCount,

        @Schema(description = "Time the document was uploaded (UTC)", example = "2026-11-25T20:01:00Z")
        Instant createdAt,

        @Schema(description = "Failure reason; null unless status is FAILED", example = "Could not extract text from PDF", nullable = true)
        String error
) {
    public Document {
        Objects.requireNonNull(docId, "docId");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Document processing(String docId, String fileName, long fileSizeBytes, Instant createdAt) {
        return new Document(docId, fileName, fileSizeBytes, DocumentStatus.PROCESSING, 0, 0, createdAt, null);
    }

    public Document ready(int pageCount, int chunkCount) {
        return new Document(docId, fileName, fileSizeBytes, DocumentStatus.READY, pageCount, chunkCount, createdAt, null);
    }

    public Document failed(String reason) {
        return new Document(docId, fileName, fileSizeBytes, DocumentStatus.FAILED, pageCount, chunkCount, createdAt, reason);
    }

    @Schema(hidden = true)
    public boolean isReady() {
        return status == DocumentStatus.READY;
    }
}
