package com.demo.aidocumentassistant.ask;

import com.demo.aidocumentassistant.ask.AskReply.Citation;
import com.demo.aidocumentassistant.common.error.AiCallException;
import com.demo.aidocumentassistant.common.error.DocumentNotFoundException;
import com.demo.aidocumentassistant.common.error.DocumentNotReadyException;
import com.demo.aidocumentassistant.document.Document;
import com.demo.aidocumentassistant.document.InMemoryDocumentRepository;
import com.demo.aidocumentassistant.embedding.EmbeddingService;
import com.demo.aidocumentassistant.ingestion.Chunk;
import com.demo.aidocumentassistant.vector.InMemoryVectorIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Real InMemoryVectorIndex, repository and PromptBuilder; EmbeddingService and ChatClient are mocks,
 * so no test reaches Ollama or Bedrock. Vectors are 2-dim so the scores are easy to read:
 * query (1,0) vs (1,0) = 1.0, vs (1,1) = 0.707, vs (0,1) = 0.0.
 */
@ExtendWith(OutputCaptureExtension.class)
class AskServiceTest {

    private static final String DOC = "doc-1";
    private static final float[] QUERY = {1, 0};

    private InMemoryDocumentRepository documents;
    private InMemoryVectorIndex index;
    private EmbeddingService embeddingService;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;
    private AskService service;

    @BeforeEach
    void setUp() {
        documents = new InMemoryDocumentRepository();
        index = new InMemoryVectorIndex();
        embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embedQuery(anyString())).thenReturn(QUERY);

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);

        service = new AskService(documents, embeddingService, index, new PromptBuilder(), builder,
                new AskProperties(5, 0.5, 40));

        documents.save(Document.processing(DOC, "policy.pdf", 1234, Instant.parse("2026-11-04T10:00:00Z")).ready(3, 3));
        index.upsert(
                List.of(chunk(0, 1, "Shipping is free for orders over 50 dollars."),
                        chunk(1, 2, "Refunds are accepted within 30 days of delivery, with receipt."),
                        chunk(2, 3, "Our office dog is called Biscuit.")),
                List.of(new float[]{1, 1}, new float[]{1, 0}, new float[]{0, 1}));
    }

    @Test
    void answersWithCitationsForTheCitedPagesOnly() {
        modelAnswers("Refunds are accepted within 30 days [page 2]. Shipping is free over 50 [page 1].", 812, 25);

        AskReply reply = service.ask(DOC, "What is the refund policy?");

        assertEquals("Refunds are accepted within 30 days [page 2]. Shipping is free over 50 [page 1].", reply.answer());
        assertEquals(812, reply.inputTokens());
        assertEquals(25, reply.outputTokens());
        assertEquals(List.of(2, 1), reply.citations().stream().map(Citation::pageNumber).toList());
        assertEquals(1.0, reply.citations().get(0).score(), 1e-9);
        assertEquals(0.707, reply.citations().get(1).score(), 1e-9);          // rounded to 3 decimals
        assertTrue(reply.citations().get(0).snippet().startsWith("Refunds are accepted"));
        assertTrue(reply.citations().get(0).snippet().endsWith("…"));          // snippet-chars = 40
    }

    @Test
    void onlyChunksAboveMinScoreReachTheModel() {
        modelAnswers("Free over 50 [page 1].", 10, 5);

        service.ask(DOC, "shipping?");

        String userMessage = sentUserMessage();
        assertTrue(userMessage.contains("<source page=\"1\">"));
        assertTrue(userMessage.contains("<source page=\"2\">"));
        assertFalse(userMessage.contains("Biscuit"));                          // score 0.0 < 0.5
        assertTrue(userMessage.contains("<question>\nshipping?\n</question>"));
    }

    @Test
    void sendsTheGroundingSystemPrompt() {
        modelAnswers("x [page 2]", 1, 1);

        service.ask(DOC, "q");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(system.capture());
        assertEquals(PromptBuilder.SYSTEM_PROMPT, system.getValue());
    }

    @Test
    void nothingAboveThresholdAnswersNotFoundWithoutCallingTheModel() {
        when(embeddingService.embedQuery(anyString())).thenReturn(new float[]{-1, -1});  // all scores <= 0

        AskReply reply = service.ask(DOC, "What is the CEO's salary?");

        assertEquals(PromptBuilder.NOT_FOUND_ANSWER, reply.answer());
        assertTrue(reply.citations().isEmpty());
        assertEquals(0, reply.inputTokens());
        assertEquals(0, reply.outputTokens());
        verifyNoInteractions(chatClient);
    }

    @Test
    void modelSayingNotFoundGivesNoCitations() {
        modelAnswers(PromptBuilder.NOT_FOUND_ANSWER, 700, 9);

        AskReply reply = service.ask(DOC, "Who founded the company?");

        assertEquals(PromptBuilder.NOT_FOUND_ANSWER, reply.answer());
        assertTrue(reply.citations().isEmpty());
        assertEquals(700, reply.inputTokens());
    }

    @Test
    void citationOfAPageThatWasNotASourceIsDropped(CapturedOutput output) {
        modelAnswers("Refunds take 30 days [page 2] and the dog is Biscuit [page 3].", 10, 5);

        AskReply reply = service.ask(DOC, "q");

        assertEquals(List.of(2), reply.citations().stream().map(Citation::pageNumber).toList());
        assertTrue(output.getOut().contains("cites page 3 which was not among the sources"));
    }

    @Test
    void questionIsStrippedAndNeverLogged(CapturedOutput output) {
        modelAnswers("x [page 2]", 1, 1);

        service.ask(DOC, "   my private question   ");

        verify(embeddingService).embedQuery("my private question");
        assertFalse(output.getOut().contains("my private question"));
        assertTrue(output.getOut().contains("inTokens=1 outTokens=1"));
    }

    @Test
    void unknownDocumentIs404() {
        assertThrows(DocumentNotFoundException.class, () -> service.ask("nope", "q"));
        verifyNoInteractions(embeddingService);
    }

    @Test
    void failedDocumentIsNotReady() {
        documents.save(Document.processing("bad", "bad.pdf", 1, Instant.now()).failed("Unreadable PDF"));

        assertThrows(DocumentNotReadyException.class, () -> service.ask("bad", "q"));
        verifyNoInteractions(embeddingService);
    }

    @Test
    void modelFailureBecomesAiCallException() {
        when(callSpec.chatResponse()).thenThrow(new RuntimeException("ThrottlingException"));

        assertThrows(AiCallException.class, () -> service.ask(DOC, "q"));
    }

    @Test
    void emptyModelResponseBecomesAiCallException() {
        when(callSpec.chatResponse()).thenReturn(new ChatResponse(List.of()));

        assertThrows(AiCallException.class, () -> service.ask(DOC, "q"));
    }

    private void modelAnswers(String text, int inTokens, int outTokens) {
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(inTokens, outTokens)).build());
        when(callSpec.chatResponse()).thenReturn(response);
    }

    private String sentUserMessage() {
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(user.capture());
        return user.getValue();
    }

    private static Chunk chunk(int index, int page, String text) {
        return new Chunk(DOC, index, page, text);
    }
}
