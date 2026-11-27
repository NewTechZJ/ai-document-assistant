/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.ask;

import com.demo.aidocumentassistant.ask.AskReply.Citation;
import com.demo.aidocumentassistant.ask.PromptBuilder.Prompt;
import com.demo.aidocumentassistant.common.error.AiCallException;
import com.demo.aidocumentassistant.common.error.DocumentNotFoundException;
import com.demo.aidocumentassistant.common.error.DocumentNotReadyException;
import com.demo.aidocumentassistant.document.Document;
import com.demo.aidocumentassistant.document.DocumentRepository;
import com.demo.aidocumentassistant.embedding.EmbeddingService;
import com.demo.aidocumentassistant.vector.SearchHit;
import com.demo.aidocumentassistant.vector.VectorIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;

/**
 * Answers a question about one document:
 * embed question -> top-K search within docId -> drop hits below min-score -> hand-built prompt -> chat model
 * -> citations for the pages the answer actually cites.
 *
 * <p>Cost: one embedding call + at most one chat call (max-tokens 500 from configuration) per question;
 * no chat call at all when nothing relevant is found. Tokens are logged; the question text is not.
 */
public class AskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AskService.class);

    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final VectorIndex vectorIndex;
    private final PromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final AskProperties properties;

    public AskService(DocumentRepository documentRepository,
                      EmbeddingService embeddingService,
                      VectorIndex vectorIndex,
                      PromptBuilder promptBuilder,
                      ChatClient.Builder chatClientBuilder,
                      AskProperties properties) {
        this.documentRepository = documentRepository;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClientBuilder.build();   // the system prompt is set per call by PromptBuilder
        this.properties = properties;
    }

    
    /**
     * Answers {@code question} using only the content of the given document.
     *
     * @throws DocumentNotFoundException  if {@code docId} does not exist
     * @throws DocumentNotReadyException  if the document status is not READY
     * @throws AiCallException            if the chat model call fails or returns nothing
     */
    public AskReply ask(String docId, String question) {
        long startNanos = System.nanoTime();
        Document document = documentRepository.findById(docId)
                .orElseThrow(() -> new DocumentNotFoundException(docId));
        if (!document.isReady()) {
            throw new DocumentNotReadyException(docId, document.status().name());
        }

        String cleanQuestion = question.strip();
        float[] queryVector = embeddingService.embedQuery(cleanQuestion);
        List<SearchHit> hits = vectorIndex.search(queryVector, properties.topK(), docId);
        List<SearchHit> relevant = hits.stream().filter(hit -> hit.score() >= properties.minScore()).toList();

        LOGGER.info("ask retrieve docId={} questionChars={} hits={} relevant={} topScore={} minScore={}",
                docId, cleanQuestion.length(), hits.size(), relevant.size(),
                hits.isEmpty() ? "-" : "%.3f".formatted(hits.getFirst().score()), properties.minScore());

        if (relevant.isEmpty()) {
            LOGGER.info("ask docId={} answer=not-found-before-model inTokens=0 outTokens=0 ms={}",
                    docId, elapsedMs(startNanos));
            return new AskReply(PromptBuilder.NOT_FOUND_ANSWER, List.of(), 0, 0);
        }

        Prompt prompt = promptBuilder.build(cleanQuestion, relevant);
        ChatResponse response = callModel(prompt, docId);
        String answer = answerText(response);
        Usage usage = response.getMetadata().getUsage();
        Integer inputTokens = usage == null ? null : usage.getPromptTokens();
        Integer outputTokens = usage == null ? null : usage.getCompletionTokens();

        List<Citation> citations = AnswerCitations.isNotFound(answer) ? List.of() : citationsFor(answer, relevant, docId);

        LOGGER.info("ask docId={} sources={} promptChars={} citedPages={} notFound={} inTokens={} outTokens={} ms={}",
                docId, relevant.size(), prompt.system().length() + prompt.user().length(),
                citations.stream().map(Citation::pageNumber).toList(), AnswerCitations.isNotFound(answer),
                inputTokens, outputTokens, elapsedMs(startNanos));

        return new AskReply(answer, citations, inputTokens, outputTokens);
    }

    /** One citation per page the answer cites (first-cited first), using the best-scoring chunk of that page. */
    private List<Citation> citationsFor(String answer, List<SearchHit> sources, String docId) {
        SequencedSet<Integer> citedPages = AnswerCitations.citedPages(answer);
        List<Citation> citations = new ArrayList<>();
        for (int page : citedPages) {
            Optional<SearchHit> best = sources.stream()
                    .filter(hit -> hit.pageNumber() == page)
                    .max(Comparator.comparingDouble(SearchHit::score));
            if (best.isPresent()) {
                citations.add(new Citation(page, snippet(best.get().chunk().text()), round3(best.get().score())));
            } else {
                LOGGER.warn("ask docId={} answer cites page {} which was not among the sources", docId, page);
            }
        }
        if (citedPages.isEmpty()) {
            LOGGER.warn("ask docId={} answer has no [page N] citations", docId);
        }
        return List.copyOf(citations);
    }

    private ChatResponse callModel(Prompt prompt, String docId) {
        ChatResponse response;
        try {
            response = chatClient.prompt()
                    .system(prompt.system())
                    .user(prompt.user())
                    .call()
                    .chatResponse();
        } catch (RuntimeException e) {
            LOGGER.error("Ask model call failed docId={}", docId, e);
            throw new AiCallException("The AI model call failed.", e);
        }
        if (response == null || response.getResult() == null) {
            LOGGER.error("Ask model returned an empty response docId={}", docId);
            throw new AiCallException("The AI model returned an empty response.", null);
        }
        return response;
    }

    private static String answerText(ChatResponse response) {
        String text = response.getResult().getOutput().getText();
        return StringUtils.hasText(text) ? text.strip() : PromptBuilder.NOT_FOUND_ANSWER;
    }

    private String snippet(String text) {
        String flat = text.strip().replaceAll("\\s+", " ");
        int max = properties.snippetChars();
        if (flat.codePointCount(0, flat.length()) <= max) {
            return flat;
        }
        return flat.substring(0, flat.offsetByCodePoints(0, max)).stripTrailing() + "…";
    }

    private static double round3(double score) {
        return Math.round(score * 1000) / 1000.0;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
