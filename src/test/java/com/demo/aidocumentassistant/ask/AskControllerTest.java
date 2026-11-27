package com.demo.aidocumentassistant.ask;

import com.demo.aidocumentassistant.ask.AskReply.Citation;
import com.demo.aidocumentassistant.common.error.AiCallException;
import com.demo.aidocumentassistant.common.error.DocumentNotFoundException;
import com.demo.aidocumentassistant.common.error.DocumentNotReadyException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AskController.class)
class AskControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AskService askService;

    @Test
    void returnsAnswerCitationsAndTokens() throws Exception {
        when(askService.ask("doc-1", "What is the refund window?")).thenReturn(new AskReply(
                "30 days [page 4].", List.of(new Citation(4, "Refunds are accepted within 30 days", 0.612)), 850, 12));

        ask("{\"docId\":\"doc-1\",\"question\":\"What is the refund window?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("30 days [page 4]."))
                .andExpect(jsonPath("$.citations[0].pageNumber").value(4))
                .andExpect(jsonPath("$.citations[0].snippet").value("Refunds are accepted within 30 days"))
                .andExpect(jsonPath("$.citations[0].score").value(0.612))
                .andExpect(jsonPath("$.inputTokens").value(850))
                .andExpect(jsonPath("$.outputTokens").value(12));
    }

    @Test
    void blankQuestionIs400AndNeverReachesTheService() throws Exception {
        ask("{\"docId\":\"doc-1\",\"question\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(askService);
    }

    @Test
    void missingDocIdIs400() throws Exception {
        ask("{\"question\":\"hi\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(askService);
    }

    @Test
    void questionOverTheCapIs400() throws Exception {
        String longQuestion = "a".repeat(AskRequest.MAX_QUESTION_CHARS + 1);
        ask("{\"docId\":\"doc-1\",\"question\":\"" + longQuestion + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(askService);
    }

    @Test
    void questionAtTheCapIsAccepted() throws Exception {
        String question = "a".repeat(AskRequest.MAX_QUESTION_CHARS);
        when(askService.ask("doc-1", question)).thenReturn(new AskReply(PromptBuilder.NOT_FOUND_ANSWER, List.of(), 0, 0));

        ask("{\"docId\":\"doc-1\",\"question\":\"" + question + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations").isEmpty());
    }

    @Test
    void unknownDocumentIs404() throws Exception {
        when(askService.ask(anyString(), anyString())).thenThrow(new DocumentNotFoundException("nope"));

        ask("{\"docId\":\"nope\",\"question\":\"hi\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void documentNotReadyIs409() throws Exception {
        when(askService.ask(anyString(), anyString())).thenThrow(new DocumentNotReadyException("d", "FAILED"));

        ask("{\"docId\":\"d\",\"question\":\"hi\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_READY"));
    }

    @Test
    void modelFailureIs502WithoutInternals() throws Exception {
        when(askService.ask(anyString(), anyString()))
                .thenThrow(new AiCallException("failed", new RuntimeException("arn:aws:bedrock:secret")));

        ask("{\"docId\":\"d\",\"question\":\"hi\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("The AI model is unavailable. Try again."));
    }

    private ResultActions ask(String json) throws Exception {
        return mockMvc.perform(post("/api/ask").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
