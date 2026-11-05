package com.demo.aidocumentassistant.common.error;

/** The PDF opened fine but has no text layer: almost always a scanned document (images only). */
public class NoExtractableTextException extends PdfExtractionException {
    public NoExtractableTextException(String message) {
        super(message);
    }
}
