/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.chat;

import com.demo.aidocumentassistant.common.error.AiCallException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer tests for {@link ChatController}: validation, JSON contract and error mapping.
 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    private static final String CHAT_URL = "/api/v1/chat";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    void returnsReplyWithAllFields() throws Exception {
        when(chatService.chat("hi")).thenReturn(new ChatReply("hello", "test-model", 5, 3, 10));

        postChat("{\"message\":\"hi\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("hello"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.inputTokens").value(5))
                .andExpect(jsonPath("$.outputTokens").value(3))
                .andExpect(jsonPath("$.latencyMs").value(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"message\":\"\"}", "{\"message\":\"   \"}", "{\"message\":null}", "{}"})
    void rejectsBlankOrMissingMessage(String body) throws Exception {
        postChat(body).andExpect(status().isBadRequest());
        verifyNoInteractions(chatService);
    }

    @Test
    void acceptsMessageAtMaxLength() throws Exception {
        when(chatService.chat(anyString())).thenReturn(new ChatReply("ok", "m", 1, 1, 1));

        postChat(messageOfLength(ChatRequest.MAX_MESSAGE_CHARS))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMessageOverMaxLength() throws Exception {
        postChat(messageOfLength(ChatRequest.MAX_MESSAGE_CHARS + 1))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(chatService);
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        postChat("{not json").andExpect(status().isBadRequest());
        verifyNoInteractions(chatService);
    }

    @Test
    void nonJsonContentTypeIsUnsupported() throws Exception {
        mockMvc.perform(post(CHAT_URL).contentType(MediaType.TEXT_PLAIN).content("hi"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(chatService);
    }

    @Test
    void aiFailureIs502WithoutProviderDetails() throws Exception {
        when(chatService.chat(anyString())).thenThrow(
                new AiCallException("The AI model call failed.", new RuntimeException("arn:aws:bedrock:secret")));

        postChat("{\"message\":\"hi\"}")
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AI_CALL_FAILED")) // use your ErrorCode value
                .andExpect(content().string(not(containsString("arn:aws"))));
    }

    private ResultActions postChat(String json) throws Exception {
        return mockMvc.perform(post(CHAT_URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String messageOfLength(int length) {
        return "{\"message\":\"" + "a".repeat(length) + "\"}";
    }
}
