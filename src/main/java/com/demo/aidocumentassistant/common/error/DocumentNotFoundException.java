package com.demo.aidocumentassistant.common.error;

/** No document with this id (404). In-memory records are lost on restart, so a stale id also ends up here. */
public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String docId) {
        super("No document with id '" + docId + "'. Upload it again if the server was restarted.");
    }
}
