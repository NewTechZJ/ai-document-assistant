package com.demo.aidocumentassistant.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @Schema(description = "User message", example = "Hello")
        @NotBlank @Size(max = 500) String message
) {
}
