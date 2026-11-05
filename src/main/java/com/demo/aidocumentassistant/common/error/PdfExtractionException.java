package com.demo.aidocumentassistant.common.error;


/**
 * Base class for "this file can't be turned into text".
 * Callers that don't care about the reason catch this one type;
 * callers that do (e.g. ApiExceptionHandler) catch the subclasses.
 * Also thrown directly for a file that starts like a PDF but can't be parsed (corrupt/truncated).
 */
public class PdfExtractionException extends RuntimeException {
    public PdfExtractionException(String message) {
        super(message);
    }

    public PdfExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
