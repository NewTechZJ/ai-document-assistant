package com.demo.aidocumentassistant.common.error;

/** The server is at its limit for concurrent document processing; the client should retry later (503). */
public class ServerBusyException extends RuntimeException {
    public ServerBusyException(String message) {
        super(message);
    }
}
