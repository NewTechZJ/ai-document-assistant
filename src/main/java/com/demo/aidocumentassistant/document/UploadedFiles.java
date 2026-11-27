/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.document;

import com.demo.aidocumentassistant.common.error.BadRequestException;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Helpers shared by the controllers that accept a multipart PDF. */
final class UploadedFiles {
    static final String DEFAULT_FILE_NAME = "upload.pdf";

    private UploadedFiles() {
    }

    /** Rejects a zero-byte upload with a 400 {@link BadRequestException}. */
    static void requireNotEmpty(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("The uploaded file is empty.");
        }
    }

    /**
     * Keeps only the last path segment: some browsers send a full client path ("C:\\Users\\me\\a.pdf").
     * The name is only stored and echoed back for display; it is never used to build a file path.
     * Falls back to {@link #DEFAULT_FILE_NAME} when the name is missing or blank.
     */
    static String safeFileName(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return DEFAULT_FILE_NAME;
        }
        int lastSeparator = Math.max(originalFileName.lastIndexOf('/'), originalFileName.lastIndexOf('\\'));
        String name = originalFileName.substring(lastSeparator + 1).strip();
        return name.isEmpty() ? DEFAULT_FILE_NAME : name;
    }

    /** IOException becomes UncheckedIOException, which ApiExceptionHandler maps to 400 UPLOAD_UNREADABLE. */
    static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }
}
