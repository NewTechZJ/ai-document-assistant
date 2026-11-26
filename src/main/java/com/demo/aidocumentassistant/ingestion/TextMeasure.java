/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.ingestion;

/**
 * Measures the size of a piece of text, in the unit the chunk limits use.
 */
public interface TextMeasure {

    /** Size of {@code text} in this measure's unit. */
    int measure(CharSequence text);

    /** Measures in UTF-16 chars, the unit of {@code targetChars}. */
    TextMeasure CHARS = CharSequence::length;
}
