package com.demo.aidocumentassistant.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.UncheckedIOException;

/** Maps our exceptions to RFC 9457 ProblemDetail responses. Spring picks the most specific handler. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** The real cause is logged; the client only gets a generic message (provider errors can leak internals). */
    @ExceptionHandler(AiCallException.class)
    ProblemDetail handleAiCallFailure(AiCallException e) {
        LOGGER.warn("AI call failed (502): {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "The AI model is unavailable. Try again.");
        problem.setTitle("AI model unavailable");
        return problem;
    }

    @ExceptionHandler(UncheckedIOException.class)
    ProblemDetail handleUploadReadFailure(UncheckedIOException e) {
        LOGGER.warn("Could not read upload: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The upload could not be read.");
        problem.setTitle("Bad request");
        return problem;
    }

    @ExceptionHandler(NotAPdfException.class)
    ProblemDetail handleNotAPdf(NotAPdfException e) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Not a PDF", e);
    }

    @ExceptionHandler(EncryptedPdfException.class)
    ProblemDetail handleEncryptedPdf(EncryptedPdfException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Encrypted PDF", e);
    }

    @ExceptionHandler(NoExtractableTextException.class)
    ProblemDetail handleNoExtractableText(NoExtractableTextException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "No extractable text", e);
    }

    /** Catch-all for the rest of the family: corrupt or truncated PDFs. */
    @ExceptionHandler(PdfExtractionException.class)
    ProblemDetail handleUnreadablePdf(PdfExtractionException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Unreadable PDF", e);
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail handleBadRequest(BadRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", e);
    }

    /** Thrown by the servlet container before our code runs, so the limit itself comes from application.properties. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException e) {
        LOGGER.warn("Upload rejected: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONTENT_TOO_LARGE, "The file is larger than the upload limit.");
        problem.setTitle("File too large");
        return problem;
    }

    @ExceptionHandler(PdfTooLargeException.class)
    ProblemDetail handlePdfTooLarge(PdfTooLargeException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "PDF too large to process", e);
    }

    @ExceptionHandler(ServerBusyException.class)
    ResponseEntity<ProblemDetail> handleBusy(ServerBusyException e) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "Server busy", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "5").body(problem);
    }

    /** Our own exception messages are written for users, so they are safe to return. */
    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException e) {
        LOGGER.warn("{} ({}): {}", title, status.value(), e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
