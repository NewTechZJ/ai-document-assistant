package com.demo.aidocumentassistant.ingestion;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Splits page text into overlapping chunks for embedding.
 * <p>
 * Three steps per page:
 * <ol>
 *   <li><b>Split</b> the page into pieces no longer than targetChars, trying the gentlest cut first:
 *       paragraph, then sentence, then whitespace, then word boundary, then (last resort) a raw cut.</li>
 *   <li><b>Pack</b> consecutive pieces into chunks of up to targetChars, starting each new chunk with
 *       the last ~overlapChars of the previous one.</li>
 *   <li><b>Flush</b> at the end of the page. If the last chunk's new text is shorter than minChars,
 *       it is merged into the previous chunk instead of becoming a tiny chunk of its own.</li>
 * </ol>
 * Size guarantee: every chunk is at most targetChars, except a merged last chunk
 * (at most targetChars + 2 + minChars - 1: the merge adds the "\n\n" separator plus fewer than minChars).
 * <p>
 * Each page is chunked on its own, so a chunk's pageNumber is always exact for citations.
 * Plain Java: no Spring, no I/O, deterministic, so it's easy to unit-test thoroughly.
 */
public class Chunker {
    private static final String SENTENCE_SEPARATOR = " ";

    private final int targetChars;
    private final int overlapChars;
    private final int minChars;

    public Chunker(ChunkingProperties properties) {
        this.targetChars = properties.targetChars();
        this.overlapChars = properties.overlapChars();
        this.minChars = properties.minChars();
    }

    public List<Chunk> chunk(String docId, List<PageText> pages) {
        Objects.requireNonNull(docId, "docId");
        Objects.requireNonNull(pages, "pages");

        List<Chunk> chunks = new ArrayList<>();
        int index = 0; // continuous across pages, so "{docId}#{index}" is unique within the document
        for (PageText page : pages) {
            if (page.isBlank()) {
                continue; // an empty page produces no chunks, but later pages keep their real numbers
            }
            for (String text : chunkPage(page.text())) {
                chunks.add(new Chunk(docId, index++, page.pageNumber(), text));
            }
        }
        return List.copyOf(chunks);
    }

    private List<String> chunkPage(String pageText) {
        List<Piece> pieces = splitIntoPieces(pageText);
        List<String> chunks = new ArrayList<>();

        // Greedy packing: keep appending pieces while they fit. When one doesn't, emit the chunk,
        // start the next one with an overlap tail of the emitted chunk, then add the piece.
        // newTextStart marks where the non-overlap text starts; flushLastChunk uses it for the minChars rule.
        StringBuilder chunkInProgress = new StringBuilder();
        int newTextStart = 0;
        Piece firstNewPiece = null; // its separator is used if the last chunk gets merged

        for (Piece piece : pieces) {
            if (chunkInProgress.isEmpty()) {
                chunkInProgress.append(piece.text());
                newTextStart = 0;
                firstNewPiece = piece;
            } else if (chunkInProgress.length() + piece.separator().length() + piece.text().length() <= targetChars) {
                chunkInProgress.append(piece.separator()).append(piece.text());
            } else {
                chunks.add(chunkInProgress.toString());
                String overlap = overlapTail(chunkInProgress, piece);
                chunkInProgress.setLength(0);
                if (!overlap.isEmpty()) {
                    chunkInProgress.append(overlap).append(piece.separator());
                }
                newTextStart = chunkInProgress.length();
                firstNewPiece = piece;
                chunkInProgress.append(piece.text());
            }
        }
        flushLastChunk(chunks, chunkInProgress, newTextStart, firstNewPiece);
        return chunks;
    }

    /**
     * Emits the chunk still being built when the page ends.
     * If its new (non-overlap) text is shorter than minChars, that text is appended to the
     * previous chunk instead, so a page never ends with a tiny chunk that is mostly overlap.
     */
    private void flushLastChunk(List<String> chunks, StringBuilder chunkInProgress,
                                int newTextStart, Piece firstNewPiece) {
        if (chunkInProgress.isEmpty()) {
            return;
        }
        int newTextLength = chunkInProgress.length() - newTextStart;
        if (!chunks.isEmpty() && newTextLength < minChars) {
            int lastIndex = chunks.size() - 1;
            chunks.set(lastIndex,
                    chunks.get(lastIndex) + firstNewPiece.separator() + chunkInProgress.substring(newTextStart));
        } else {
            chunks.add(chunkInProgress.toString());
        }
    }

    private List<Piece> splitIntoPieces(String pageText) {
        List<Piece> pieces = new ArrayList<>();
        for (String rawParagraph : TextNormalizer.PARAGRAPH_BREAK.split(pageText)) {
            String paragraph = rawParagraph.strip();
            if (paragraph.isEmpty()) {
                continue;
            }
            if (paragraph.length() <= targetChars) {
                pieces.add(new Piece(paragraph, TextNormalizer.PARAGRAPH_SEPARATOR));
                continue;
            }
            // Paragraph too long: split it into sentences (and over-long sentences into parts).
            // Packing glues neighbouring sentences back together, so chunks still end on sentence boundaries.
            boolean isFirstPieceOfParagraph = true;
            for (Piece sentence : splitSentences(paragraph)) {
                List<Piece> parts = sentence.text().length() <= targetChars
                        ? List.of(sentence)
                        : hardSplit(sentence.text(), sentence.separator());
                for (Piece part : parts) {
                    pieces.add(isFirstPieceOfParagraph
                            ? part.withSeparator(TextNormalizer.PARAGRAPH_SEPARATOR)
                            : part);
                    isFirstPieceOfParagraph = false;
                }
            }
        }
        return pieces;
    }

    /**
     * JDK's BreakIterator knows sentence rules for many languages (handles "?", "!", quotes, etc.).
     * It sometimes splits after abbreviations like "Dr."; harmless here, because packing rejoins pieces.
     * Created per call on purpose: BreakIterator is stateful and not thread-safe, and Chunker is a shared bean.
     */
    private static List<Piece> splitSentences(String paragraph) {
        BreakIterator sentenceBoundaries = BreakIterator.getSentenceInstance(Locale.ROOT);
        sentenceBoundaries.setText(paragraph);
        List<Piece> sentences = new ArrayList<>();
        int previousEnd = 0;
        int start = sentenceBoundaries.first();
        for (int end = sentenceBoundaries.next(); end != BreakIterator.DONE;
             start = end, end = sentenceBoundaries.next()) {
            int textStart = skipWhitespace(paragraph, start);
            int textEnd = trimEnd(paragraph, textStart, end);
            if (textStart < textEnd) {
                // separator = what really sat between the previous sentence and this one: " " or ""
                sentences.add(new Piece(paragraph.substring(textStart, textEnd),
                        paragraph.substring(previousEnd, textStart)));
                previousEnd = textEnd;
            }
        }
        return sentences;
    }

    private static int trimEnd(String text, int start, int end) {
        int i = end;
        while (i > start && Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    /**
     * Last resort for a "sentence" longer than targetChars (tables, lists without periods, run-on text).
     * Parts are at most targetChars - overlapChars long, so the next chunk still has room for overlap.
     * Works with offsets into {@code text} (no repeated substring copies), so it is linear in the text length.
     */
    private List<Piece> hardSplit(String text, String firstSeparator) {
        int maxPartLength = targetChars - overlapChars;
        BreakIterator wordBoundaries = null;
        List<Piece> parts = new ArrayList<>();
        String separator = firstSeparator;
        int start = 0;
        while (text.length() - start > maxPartLength) {
            int limit = start + maxPartLength;
            int cutIndex = lastWhitespaceAtOrBefore(text, start, limit);
            if (cutIndex <= start) {
                // No space in the window, e.g. Chinese/Japanese: cut at the last word boundary.
                if (wordBoundaries == null) {
                    wordBoundaries = BreakIterator.getWordInstance(Locale.ROOT);
                    wordBoundaries.setText(text);
                }
                cutIndex = wordBoundaries.preceding(limit + 1); // last boundary <= limit
                if (cutIndex <= start) {
                    // One "word" longer than a whole part (a URL, base64...). Cutting it is the only option.
                    cutIndex = limit;
                }
                // Both cuts above can land inside a character cluster: the raw cut inside an emoji's surrogate pair,
                // and the JDK word iterator right after a ZWJ inside an emoji sequence such as 👨‍👩‍👧.
                cutIndex = stepBackToSafeCut(text, start, cutIndex);
            }
            int partEnd = trimEnd(text, start, cutIndex);
            parts.add(new Piece(text.substring(start, partEnd), separator));
            int next = skipWhitespace(text, cutIndex);
            separator = text.substring(partEnd, next); // skipped whitespace, or "" for a mid-word cut
            start = next;
        }
        if (start < text.length()) {
            parts.add(new Piece(text.substring(start), separator));
        }
        return parts;
    }

    /** Index of the last whitespace in (start, limit], or -1 if there is none. */
    private static int lastWhitespaceAtOrBefore(String text, int start, int limit) {
        for (int i = limit; i > start; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * Moves a cut left until it is not inside a surrogate pair and not next to a ZWJ/ZWNJ, which belong to
     * the characters on both sides (Indic conjuncts, Persian words, emoji sequences). Never returns <= start.
     */
    private static int stepBackToSafeCut(String text, int start, int cutIndex) {
        int cut = cutIndex;
        while (cut > start + 1 && (Character.isLowSurrogate(text.charAt(cut))
                || isJoiner(text.charAt(cut)) || isJoiner(text.charAt(cut - 1)))) {
            cut--;
        }
        return cut;
    }

    /** Zero-width non-joiner (U+200C) or zero-width joiner (U+200D). */
    private static boolean isJoiner(char c) {
        return c == '\u200C' || c == '\u200D';
    }

    /**
     * The end of the finished chunk, repeated at the start of the next one.
     * Shrinks the overlap when the next piece is large, so the new chunk still fits in targetChars.
     * Starts on a word boundary so the overlap never begins with half a word.
     */
    private String overlapTail(CharSequence finishedChunk, Piece nextPiece) {
        int overlapBudget = Math.min(overlapChars,
                targetChars - nextPiece.text().length() - nextPiece.separator().length());
        if (overlapBudget <= 0) {
            return "";
        }
        int length = finishedChunk.length();
        int start = Math.max(0, length - overlapBudget);
        // Walk forward until the previous char is whitespace, so the overlap starts at the beginning of a word.
        // If the window has no whitespace (one long token), start reaches length and the overlap is empty.
        while (start > 0 && start < length && !Character.isWhitespace(finishedChunk.charAt(start - 1))) {
            start++;
        }
        return finishedChunk.subSequence(start, length).toString().strip();
    }

    /**
     * A piece of text no longer than targetChars, and the exact text that separated it from the
     * previous piece on the page ("\n\n" between paragraphs, " " between English sentences, "" in CJK).
     */
    private record Piece(String text, String separator) {
        Piece withSeparator(String newSeparator) {
            return new Piece(text, newSeparator);
        }
    }
}
