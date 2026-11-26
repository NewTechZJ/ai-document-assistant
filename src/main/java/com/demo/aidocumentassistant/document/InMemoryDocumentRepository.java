/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.document;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link DocumentRepository}.
 * Contents are lost on restart, together with the in-memory vector index.
 */
@Repository
public class InMemoryDocumentRepository implements DocumentRepository {

    private final Map<String, Document> documents = new ConcurrentHashMap<>();

    @Override
    public void save(Document document) {
        Objects.requireNonNull(document, "document");
        documents.put(document.docId(), document);
    }

    @Override
    public Optional<Document> findById(String docId) {
        return docId == null ? Optional.empty() : Optional.ofNullable(documents.get(docId));
    }

    /** Returns all documents, newest first. */
    @Override
    public List<Document> findAll() {
        return documents.values().stream()
                .sorted(Comparator.comparing(Document::createdAt).reversed().thenComparing(Document::docId))
                .toList();
    }
}
