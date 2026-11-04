package com.demo.aidocumentassistant.chat;

import com.demo.aidocumentassistant.common.error.AiCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);
    private final ChatClient chatClient;
    private final String configuredModel;

    public ChatService(ChatClient.Builder chatClientBuilder, ChatModel chatModel) {
        this.chatClient = chatClientBuilder
                .defaultSystem("You are the assistant inside an AI Document Assistant app. Answer clearly and briefly.")
                .build();
        // Bedrock: from spring.ai.bedrock.converse.chat.options.model
        // Ollama:  from spring.ai.ollama.chat.options.model
        this.configuredModel = chatModel.getOptions().getModel();
    }

    public ChatReply chat(String message) {
        long startTime = System.nanoTime();
        try {
            LOGGER.debug("Sending message to chat client: {}", message);
            var response = chatClient
                    .prompt()
                    .user(message)
                    .call()
                    .chatResponse();
            var answer = response.getResult().getOutput().getText();
            var meta = response.getMetadata();
            var usage = meta.getUsage();
            var model = StringUtils.hasText(meta.getModel()) ? meta.getModel() : configuredModel;
            var latencyMs = (System.nanoTime() - startTime) / 1_000_000;

            LOGGER.info("chat model={} inTokens={} outTokens={} ms={}",
                    model,
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    latencyMs);

            return new ChatReply(
                    answer,
                    response.getMetadata().getModel(),
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    latencyMs
            );
        } catch (Exception e) {
            LOGGER.error("Error sending message to chat client. AI call failed", e);
            throw new AiCallException("The AI model call failed: " + e.getMessage(), e);
        }
    }
}
