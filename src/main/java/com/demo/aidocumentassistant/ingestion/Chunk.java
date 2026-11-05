package com.demo.aidocumentassistant.ingestion;

/**
 * One piece of a document that will become one vector.
 *
 * @param docId      which document it belongs to
 * @param index      0-based position in the whole document, continuous across pages
 *                   (later the S3 Vectors key is "{docId}#{index}")
 * @param pageNumber 1-based page the text came from; a chunk never spans two pages
 * @param text       the chunk text, including the overlap copied from the previous chunk
 */
public record Chunk(String docId, int index, int pageNumber, String text) {
    /** Length in UTF-16 chars, which is the same unit as targetChars. */
    public int charCount() {
        return text.length();
    }
}
