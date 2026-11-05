package com.demo.aidocumentassistant.common.error;

/** The PDF needs a password to open, or its permissions forbid text extraction. */
public class EncryptedPdfException extends PdfExtractionException {
    public EncryptedPdfException(String message) {
        super(message);
    }
    public EncryptedPdfException(String message, Throwable cause) {
        super(message, cause);
    }
}
