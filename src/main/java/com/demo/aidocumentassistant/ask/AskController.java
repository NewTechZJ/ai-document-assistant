/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.ask;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for asking questions about an uploaded document.
 */
@Tag(name = "Ask", description = "Ask questions about an uploaded document")
@RestController
@RequestMapping("/api/ask")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @Operation(
            summary = "Ask a question about a document",
            description = "Answers using only the content of the given document, and cites the pages used. "
                    + "The document must have status READY.")
    @ApiResponse(responseCode = "200", description = "Answer generated")
    @ApiResponse(responseCode = "400", description = "Invalid request, for example a blank question")
    @ApiResponse(responseCode = "404", description = "Document not found")
    @ApiResponse(responseCode = "409", description = "Document is not ready yet")
    @PostMapping
    public AskReply ask(@Valid @RequestBody AskRequest request) {
        return askService.ask(request.docId(), request.question());
    }
}
