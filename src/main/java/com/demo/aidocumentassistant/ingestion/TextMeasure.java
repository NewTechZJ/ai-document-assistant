package com.demo.aidocumentassistant.ingestion;

/** How big a piece of text is, in the unit the chunk limits use. */
public interface TextMeasure {
    int measure(CharSequence text);

    TextMeasure CHARS = CharSequence::length;   // today's behaviour
}