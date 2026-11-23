/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.document;

import com.demo.aidocumentassistant.ingestion.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST endpoint for uploading and listing PDF documents.
 *
 * <p>Upload processes the PDF right away (extract, chunk, embed) before returning.
 */
@Tag(name = "Documents", description = "Upload and list PDF documents")
@RestController
@RequestMapping(path = "/api/v1/documents", produces = MediaType.APPLICATION_JSON_VALUE)
public class DocumentController {

    private final IngestionService ingestionService;
    private final DocumentRepository documentRepository;

    public DocumentController(
            IngestionService ingestionService,
            DocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
    }

    @Operation(summary = "List all documents")
    @ApiResponse(responseCode = "200", description = "Document list")
    @GetMapping
    public List<Document> list() {
        return documentRepository.findAll();
    }

    @Operation(summary = "Upload a PDF and make it ready for questions")
    @ApiResponse(responseCode = "201", description = "Document stored and processed")
    @ApiResponse(responseCode = "400", description = "File missing or empty")
    @ApiResponse(responseCode = "413", description = "File larger than the upload limit")
    @ApiResponse(responseCode = "415", description = "File is not a PDF")
    @ApiResponse(responseCode = "422", description = "PDF is encrypted, has no text, or is too large")
    @ApiResponse(responseCode = "502", description = "AI embedding call failed")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Document upload(@RequestPart("file") MultipartFile file) {
        UploadedFiles.requireNotEmpty(file);
        return ingestionService.ingest(
                UploadedFiles.safeFileName(file.getOriginalFilename()),
                UploadedFiles.readBytes(file));
    }
}
