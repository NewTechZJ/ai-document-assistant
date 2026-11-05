package com.demo.aidocumentassistant.common.error;

public class NotAPdfException extends PdfExtractionException {
    public NotAPdfException(String message) {
        super(message);
    }
}
