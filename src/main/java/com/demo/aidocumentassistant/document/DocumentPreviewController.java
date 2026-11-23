/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.document;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Dev-only endpoint that shows how a PDF would be split into chunks, without storing anything.
 *
 * <p>Only active in the {@code local} profile.
 */
@Tag(name = "Documents")
@Profile("local")
@RestController
@RequestMapping(path = "/api/v1/documents", produces = MediaType.APPLICATION_JSON_VALUE)
public class DocumentPreviewController {

    private final DocumentPreviewService documentPreviewService;

    public DocumentPreviewController(DocumentPreviewService documentPreviewService) {
        this.documentPreviewService = documentPreviewService;
    }

    @Operation(summary = "Preview how a PDF is chunked (dev only)")
    @ApiResponse(responseCode = "200", description = "Pages and chunks")
    @ApiResponse(responseCode = "400", description = "File missing or empty")
    @ApiResponse(responseCode = "413", description = "File larger than the upload limit")
    @ApiResponse(responseCode = "415", description = "File is not a PDF")
    @ApiResponse(responseCode = "422", description = "PDF is encrypted, has no text, or is too large")
    @ApiResponse(responseCode = "503", description = "Too many previews running; retry later")
    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentPreview preview(
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Include the full text of each chunk")
            @RequestParam(defaultValue = "false") boolean includeChunkText) {
        UploadedFiles.requireNotEmpty(file);
        return documentPreviewService.preview(
                UploadedFiles.safeFileName(file.getOriginalFilename()),
                UploadedFiles.readBytes(file),
                includeChunkText);
    }
}
