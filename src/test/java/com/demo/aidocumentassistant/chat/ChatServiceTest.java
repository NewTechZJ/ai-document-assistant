package com.demo.aidocumentassistant.chat;

import com.demo.aidocumentassistant.common.error.AiCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit test: ChatClient, ChatModel and ChatResponse are Mockito deep-stub mocks, no Spring context. */
@ExtendWith(OutputCaptureExtension.class)
class ChatServiceTest {

    private static final String SYSTEM_PROMPT = "You are a test assistant.";
    private static final String CONFIGURED_MODEL = "us.anthropic.claude-haiku-4-5-20251001-v1:0";

    private ChatClient.Builder builder;
    private ChatClient chatClient;
    private ChatService service;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);

        ChatModel chatModel = mock(ChatModel.class, RETURNS_DEEP_STUBS);
        when(chatModel.getOptions().getModel()).thenReturn(CONFIGURED_MODEL);

        service = new ChatService(builder, chatModel, SYSTEM_PROMPT);
    }

    @Test
    void usesTheConfiguredSystemPrompt() {
        verify(builder).defaultSystem(SYSTEM_PROMPT);
    }

    @Test
    void returnsTextTokensAndModel() {
        modelAnswers(response("Hello!", "model-from-response", 12, 3));

        ChatReply reply = service.chat("hi");

        assertEquals("Hello!", reply.reply());
        assertEquals("model-from-response", reply.model());
        assertEquals(12, reply.inputTokens());
        assertEquals(3, reply.outputTokens());
        assertTrue(reply.latencyMs() >= 0);
        verify(chatClient.prompt()).user("hi");
    }

    @Test
    void fallsBackToTheConfiguredModelWhenTheResponseHasNone() {
        // Regression: Bedrock Converse returns no model id; the reply used to contain null.
        modelAnswers(response("Hi", null, 1, 1));
        assertEquals(CONFIGURED_MODEL, service.chat("hi").model());

        modelAnswers(response("Hi", "  ", 1, 1));
        assertEquals(CONFIGURED_MODEL, service.chat("hi").model());
    }

    @Test
    void blankAnswerIsReturnedAsIs() {
        modelAnswers(response("", "m", 1, 0));

        assertEquals("", service.chat("hi").reply());
    }

    @Test
    void nullResponseIsAnAiCallFailure() {
        modelAnswers(null);

        assertThrows(AiCallException.class, () -> service.chat("hi"));
    }

    @Test
    void responseWithoutResultIsAnAiCallFailure() {
        ChatResponse empty = mock(ChatResponse.class);
        when(empty.getResult()).thenReturn(null);
        modelAnswers(empty);

        assertThrows(AiCallException.class, () -> service.chat("hi"));
    }

    @Test
    void providerErrorBecomesAiCallExceptionWithGenericMessageAndOriginalCause() {
        RuntimeException providerError = new RuntimeException("AccessDenied arn:aws:bedrock:us-east-1:123456789012");
        when(chatClient.prompt().user(anyString()).call().chatResponse()).thenThrow(providerError);

        AiCallException e = assertThrows(AiCallException.class, () -> service.chat("hi"));

        assertEquals("The AI model call failed.", e.getMessage());
        assertSame(providerError, e.getCause());
    }

    @Test
    void logsSizesButNeverTheUserMessage(CapturedOutput output) {
        modelAnswers(response("ok", "m", 1, 1));

        service.chat("my secret medical question");

        assertTrue(output.getAll().contains("messageChars=26"), output.getAll());
        assertFalse(output.getAll().contains("secret medical"), "user prompts must not be logged");
    }

    @Test
    void failureLogDoesNotContainTheUserMessage(CapturedOutput output) {
        when(chatClient.prompt().user(anyString()).call().chatResponse()).thenThrow(new RuntimeException("boom"));

        assertThrows(AiCallException.class, () -> service.chat("my secret medical question"));

        assertFalse(output.getAll().contains("secret medical"), "user prompts must not be logged");
    }

    // ---------------------------------------------------------------- helpers

    private void modelAnswers(ChatResponse response) {
        when(chatClient.prompt().user(anyString()).call().chatResponse()).thenReturn(response);
    }

    private static ChatResponse response(String text, String model, int inTokens, int outTokens) {
        ChatResponse response = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(response.getResult().getOutput().getText()).thenReturn(text);
        when(response.getMetadata().getModel()).thenReturn(model);
        when(response.getMetadata().getUsage().getPromptTokens()).thenReturn(inTokens);
        when(response.getMetadata().getUsage().getCompletionTokens()).thenReturn(outTokens);
        return response;
    }
}
