package com.demo.aidocumentassistant.chat;

public record ChatReply(
        String reply,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        long latencyMs
) {
}
