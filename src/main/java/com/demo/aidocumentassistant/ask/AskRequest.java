/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.ask;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/ask}.
 */
@Schema(description = "Question to ask about an uploaded document")
public record AskRequest(

        @Schema(description = "Identifier of the document to query; it must have status READY",
                example = "3f2b8c1e-6a4d-4e9b-9d2a-1c7f5e8a0b34",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 64)
        @NotBlank
        @Size(max = 64)
        String docId,

        @Schema(description = "Question to answer from the document content",
                example = "What is the refund policy?",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = AskRequest.MAX_QUESTION_CHARS)
        @NotBlank
        @Size(max = AskRequest.MAX_QUESTION_CHARS)
        String question
) {
    public static final int MAX_QUESTION_CHARS = 500;
}
