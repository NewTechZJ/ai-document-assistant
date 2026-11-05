package com.demo.aidocumentassistant.common.error;

/**
 * Stable, machine-readable error codes, returned as the "code" property of every ProblemDetail.
 * Clients switch on these, so never rename or reuse a value; only add new ones.
 */
public enum ErrorCode {
    // our own errors
    NOT_A_PDF,
    PDF_ENCRYPTED,
    PDF_NO_TEXT,
    PDF_TOO_LARGE,
    PDF_UNREADABLE,
    UPLOAD_UNREADABLE,
    BAD_REQUEST,
    SERVER_BUSY,
    AI_UNAVAILABLE,
    // framework errors
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    MISSING_PART,
    UNSUPPORTED_MEDIA_TYPE,
    FILE_TOO_LARGE,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    INTERNAL_ERROR
}
