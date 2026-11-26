package com.demo.aidocumentassistant.common.error;

/** The document exists but can't be searched yet, or its ingestion failed (409). */
public class DocumentNotReadyException extends RuntimeException {
    public DocumentNotReadyException(String docId, String status) {
        super("Document '" + docId + "' is " + status + ", not READY.");
    }
}
