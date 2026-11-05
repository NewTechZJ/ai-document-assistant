package com.demo.aidocumentassistant.chat;

import com.demo.aidocumentassistant.common.error.AiCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);
    private final ChatClient chatClient;
    private final String configuredModel;

    public ChatService(
            ChatClient.Builder chatClientBuilder,
            ChatModel chatModel,
            @Value("${app.chat.system-prompt}") String systemPrompt) {
        this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();

        // Converse responses carry no model id, so this configured value is the fallback reported in ChatReply.
        // Bedrock: spring.ai.bedrock.converse.chat.options.model · Ollama: spring.ai.ollama.chat.options.model
        this.configuredModel = chatModel.getOptions().getModel();
    }

    public ChatReply chat(String message) {
        var startTime = System.nanoTime();

        ChatResponse response = callModel(message);
        if (response == null || response.getResult() == null) {
            LOGGER.error("AI call returned an empty response messageChars={}", message.length());
            throw new AiCallException("The AI model returned an empty response.", null);
        }

        var replyText = response.getResult().getOutput().getText();
        if (!StringUtils.hasText(replyText)) {
            LOGGER.warn("AI call returned no text messageChars={}", message.length());
        }

        var metadata = response.getMetadata();
        var usage = metadata.getUsage();
        var model = StringUtils.hasText(metadata.getModel()) ? metadata.getModel() : configuredModel;
        var latencyMs = (System.nanoTime() - startTime) / 1_000_000;

        LOGGER.info("chat model={}  messageChars={} inTokens={} outTokens={} ms={}",
                model,
                message.length(),
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                latencyMs);

        return new ChatReply(
                replyText,
                model,
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                latencyMs
        );
    }

    /** Only the network call to the model is wrapped, so only real AI failures become a 502. */
    private ChatResponse callModel(String message) {
        try {
            return chatClient.prompt().user(message).call().chatResponse();
        } catch (RuntimeException e) {
            LOGGER.error("AI call failed messageChars={}", message.length(), e);
            throw new AiCallException("The AI model call failed.", e);
        }
    }
}
