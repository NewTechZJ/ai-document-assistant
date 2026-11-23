/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for direct chat with the AI model.
 *
 * <p>Answers are not based on uploaded documents. Use {@code /api/v1/ask} for document questions.
 */
@Tag(name = "Chat", description = "Direct chat with the AI model")
@RestController
@RequestMapping(path = "/api/v1/chat", produces = MediaType.APPLICATION_JSON_VALUE)
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "Send a message to the AI model")
    @ApiResponse(responseCode = "200", description = "Model reply")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "502", description = "AI model call failed")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatReply chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request.message());
    }
}
