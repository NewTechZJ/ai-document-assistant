/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/chat}.
 */
@Schema(description = "Message to send to the AI model")
public record ChatRequest(
        @Schema(description = "User message", 
                example = "Hello",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = ChatRequest.MAX_MESSAGE_CHARS)
        @NotBlank
        @Size(max = ChatRequest.MAX_MESSAGE_CHARS)
        String message
) {
        public static final int MAX_MESSAGE_CHARS = 500;
}
