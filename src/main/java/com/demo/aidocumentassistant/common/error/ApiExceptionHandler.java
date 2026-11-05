package com.demo.aidocumentassistant.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.UncheckedIOException;

/**
 * Maps exceptions to RFC 9457 ProblemDetail responses. Every response carries a stable "code" property
 * (see {@link ErrorCode}); title and detail are for humans and may change.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
    static final String CODE_PROPERTY = "code";

    /** The real cause is logged; the client only gets a generic message (provider errors can leak internals). */
    @ExceptionHandler(AiCallException.class)
    ProblemDetail handleAiCallFailure(AiCallException e) {
        LOGGER.warn("AI call failed (502): {}", e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, ErrorCode.AI_UNAVAILABLE, "AI model unavailable",
                "The AI model is unavailable. Try again.");
    }

    @ExceptionHandler(NotAPdfException.class)
    ProblemDetail handleNotAPdf(NotAPdfException e) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.NOT_A_PDF, "Not a PDF", e);
    }

    @ExceptionHandler(EncryptedPdfException.class)
    ProblemDetail handleEncryptedPdf(EncryptedPdfException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.PDF_ENCRYPTED, "Encrypted PDF", e);
    }

    @ExceptionHandler(NoExtractableTextException.class)
    ProblemDetail handleNoExtractableText(NoExtractableTextException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.PDF_NO_TEXT, "No extractable text", e);
    }

    @ExceptionHandler(PdfTooLargeException.class)
    ProblemDetail handlePdfTooLarge(PdfTooLargeException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.PDF_TOO_LARGE, "PDF too large to process", e);
    }

    /** Catch-all for the rest of the family: corrupt or truncated PDFs. */
    @ExceptionHandler(PdfExtractionException.class)
    ProblemDetail handleUnreadablePdf(PdfExtractionException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.PDF_UNREADABLE, "Unreadable PDF", e);
    }

    @ExceptionHandler(UncheckedIOException.class)
    ProblemDetail handleUploadReadFailure(UncheckedIOException e) {
        LOGGER.warn("Could not read upload: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.UPLOAD_UNREADABLE, "Bad request",
                "The upload could not be read.");
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail handleBadRequest(BadRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "Bad request", e);
    }

    @ExceptionHandler(ServerBusyException.class)
    ResponseEntity<ProblemDetail> handleBusy(ServerBusyException e) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVER_BUSY, "Server busy", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "5").body(problem);
    }

    /** Anything unexpected: log it fully, tell the client nothing internal. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        LOGGER.error("Unexpected error (500)", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Internal error",
                "Something went wrong. Try again later.");
    }

    // ---------------------------------------------------------------- Spring MVC's own exceptions

    /** Every exception handled by ResponseEntityExceptionHandler passes through here. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            problem.setProperty(CODE_PROPERTY, codeFor(ex, statusCode).name());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static ErrorCode codeFor(Exception ex, HttpStatusCode status) {
        return switch (ex) {
            case MethodArgumentNotValidException e -> ErrorCode.VALIDATION_FAILED;
            case HandlerMethodValidationException e -> ErrorCode.VALIDATION_FAILED;
            case HttpMessageNotReadableException e -> ErrorCode.MALFORMED_REQUEST;
            case MissingServletRequestPartException e -> ErrorCode.MISSING_PART;
            case HttpMediaTypeNotSupportedException e -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case MaxUploadSizeExceededException e -> ErrorCode.FILE_TOO_LARGE;
            case NoResourceFoundException e -> ErrorCode.NOT_FOUND;
            case HttpRequestMethodNotSupportedException e -> ErrorCode.METHOD_NOT_ALLOWED;
            default -> status.is4xxClientError() ? ErrorCode.BAD_REQUEST : ErrorCode.INTERNAL_ERROR;
        };
    }

    // ---------------------------------------------------------------- helpers

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String title, RuntimeException e) {
        LOGGER.warn("{} ({} {}): {}", title, status.value(), code, e.getMessage());
        return problem(status, code, title, e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty(CODE_PROPERTY, code.name());
        return problem;
    }
}