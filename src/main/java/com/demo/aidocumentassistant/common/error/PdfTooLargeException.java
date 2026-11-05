package com.demo.aidocumentassistant.common.error;

/** The PDF is valid but too big to process within the configured limits. */
public class PdfTooLargeException extends PdfExtractionException {
    public PdfTooLargeException(String message) { super(message); }
}