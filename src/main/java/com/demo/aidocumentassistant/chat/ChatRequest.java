package com.demo.aidocumentassistant.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @Schema(description = "User message", example = "Hello")
        @NotBlank
        @Size(max = ChatRequest.MAX_MESSAGE_CHARS)
        String message
) {
        public static final int MAX_MESSAGE_CHARS = 500;
}
