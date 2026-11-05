package com.demo.aidocumentassistant.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Direct unit tests of the handler methods. The HTTP side is covered by the controller tests;
 * 413 is tested here because MockMvc cannot trigger the container's upload limit.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void aiFailureIs502AndDoesNotLeakTheProviderError() {
        AiCallException e = new AiCallException("The AI model call failed: AccessDenied arn:aws:iam::123456789012",
                new RuntimeException("arn:aws:iam::123456789012"));

        ProblemDetail problem = handler.handleAiCallFailure(e);

        assertEquals(502, problem.getStatus());
        assertEquals("AI model unavailable", problem.getTitle());
        assertFalse(problem.getDetail().contains("arn:aws"), problem.getDetail());
    }

    @Test
    void unexpectedErrorIs500WithCodeAndNoInternals() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("db password=hunter2"));

        assertEquals(500, problem.getStatus());
        assertEquals("INTERNAL_ERROR", problem.getProperties().get("code"));
        assertFalse(problem.getDetail().contains("hunter2"));
    }

    @Test
    void pdfFamilyStatusCodes() {
        assertEquals(415, handler.handleNotAPdf(new NotAPdfException("x")).getStatus());
        assertEquals(422, handler.handleEncryptedPdf(new EncryptedPdfException("x")).getStatus());
        assertEquals(422, handler.handleNoExtractableText(new NoExtractableTextException("x")).getStatus());
        assertEquals(422, handler.handleUnreadablePdf(new PdfExtractionException("x")).getStatus());
    }

    @Test
    void ownMessagesAreReturnedAsDetail() {
        ProblemDetail problem = handler.handleBadRequest(new BadRequestException("The uploaded file is empty."));

        assertEquals(400, problem.getStatus());
        assertEquals("Bad request", problem.getTitle());
        assertEquals("The uploaded file is empty.", problem.getDetail());
    }
}
