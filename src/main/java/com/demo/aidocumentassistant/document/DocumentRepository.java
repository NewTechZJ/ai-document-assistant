/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.document;

import java.util.List;
import java.util.Optional;

/**
 * Storage for {@link Document} records (backed by DynamoDB).
 */
public interface DocumentRepository {

    /** Creates or replaces the record with the same {@code docId}. */
    void save(Document document);

    /** Returns the document, or empty if {@code docId} is not found. */
    Optional<Document> findById(String docId);

    /** Returns all documents; empty list if none. */
    List<Document> findAll();
}
