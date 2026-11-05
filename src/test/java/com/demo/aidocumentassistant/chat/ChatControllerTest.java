package com.demo.aidocumentassistant.chat;

import com.demo.aidocumentassistant.common.error.AiCallException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {
    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    ChatService chatService;

    @Test
    void returnsAnswer() throws Exception {
        when(chatService.chat("hi")).thenReturn(new ChatReply("hello", "test-model", 5, 3, 10));
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("hello"));
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMessageLongerThan2000Chars() throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + "a".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(chatService);
    }

    @Test
    void acceptsMessageOfExactly2000Chars() throws Exception {
        when(chatService.chat(anyString())).thenReturn(new ChatReply("ok", "m", 1, 1, 1));
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + "a".repeat(2000) + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void aiFailureIs502WithoutProviderDetails() throws Exception {
        when(chatService.chat(anyString())).thenThrow(
                new AiCallException("The AI model call failed.", new RuntimeException("arn:aws:bedrock:secret")));
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("AI model unavailable"))
                .andExpect(content().string(not(containsString("arn:aws"))));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
