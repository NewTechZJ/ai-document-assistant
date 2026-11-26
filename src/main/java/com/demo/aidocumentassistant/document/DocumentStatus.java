/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.document;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Processing state of a {@link Document}.
 */
@Schema(description = """
        Processing state of a document: \
        UPLOADED (file stored, not yet processed), \
        PROCESSING (text is being extracted and indexed), \
        READY (can be queried), \
        FAILED (processing failed, see the error field)""")
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    READY,
    FAILED
}
