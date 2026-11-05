package com.demo.aidocumentassistant.ingestion;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Cleans raw PDFBox output into tidy paragraphs.
 * <p>
 * Output contract (the Chunker relies on it):
 * <ul>
 *   <li>paragraphs are separated by exactly one blank line ({@link #PARAGRAPH_SEPARATOR})</li>
 *   <li>inside a paragraph there are no newlines and no double spaces</li>
 *   <li>no leading/trailing whitespace</li>
 * </ul>
 * Package-private and stateless: it's a helper of PdfTextExtractor, tested directly.
 */
final class TextNormalizer {

    /** Two or more newlines (after lines are trimmed) = a paragraph break. Shared with Chunker. */
    static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n{2,}");
    /** What {@link #normalize} puts between paragraphs. Shared with Chunker. */
    static final String PARAGRAPH_SEPARATOR = "\n\n";

    // Invisible characters commonly found in PDFs: soft hyphens, zero-width spaces, and BOMs.
    // These carry no semantic meaning in extracted text.
    // Note: ZWJ (U+200D) and ZWNJ (U+200C) are deliberately preserved because they
    // affect the spelling and rendering of Indic and Persian words, and maintain emoji sequences.
    private static final Pattern INVISIBLE_CHARS = Pattern.compile("[\\u00AD\\u200B\\u2060\\uFEFF]");

    // Control characters except tab (\t) and newline (\n).
    private static final Pattern CONTROL_CHARS_EXCEPT_TAB_NEWLINE = Pattern.compile("[\\p{Cc}&&[^\\t\\n]]");
    // Any horizontal whitespace, including no-break and other Unicode spaces.
    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[\\h]+");
    // "extrac-\ntion" -> "extraction". Letter, hyphen (ASCII or U+2010), line break, lowercase letter.
    // Trade-off: also joins real compounds that wrap at a line end ("self-\nservice" -> "selfservice").
    // Wrapped words are far more common in PDFs, and embeddings are barely affected either way.
    private static final Pattern HYPHENATED_BREAK = Pattern.compile("(\\p{L})[-\\u2010]\\n(\\p{Ll})");

    private TextNormalizer() {
    }

    static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        // NFKC turns ligatures (U+FB01 "ﬁ") into plain letters ("fi") and full-width forms into normal ones,
        // so "ﬁle" and "file" embed and match the same way.
        String text = Normalizer.isNormalized(raw, Normalizer.Form.NFKC)
                ? raw
                : Normalizer.normalize(raw, Normalizer.Form.NFKC);
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        text = INVISIBLE_CHARS.matcher(text).replaceAll("");
        text = CONTROL_CHARS_EXCEPT_TAB_NEWLINE.matcher(text).replaceAll(" ");

        // Clean each line first so the patterns below see "word-\nword", not "word-  \n   word".
        StringBuilder trimmedLines = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            trimmedLines.append(HORIZONTAL_SPACE.matcher(line).replaceAll(" ").strip()).append('\n');
        }
        text = trimmedLines.toString();

        text = HYPHENATED_BREAK.matcher(text).replaceAll("$1$2");

        // Unwrap: inside a paragraph, a single line break is just the PDF's line wrapping.
        StringBuilder result = new StringBuilder(text.length());
        for (String paragraph : PARAGRAPH_BREAK.split(text)) {
            String joined = paragraph.replace('\n', ' ').strip();
            if (!joined.isEmpty()) {
                if (!result.isEmpty()) {
                    result.append(PARAGRAPH_SEPARATOR);
                }
                result.append(joined);
            }
        }
        return result.toString();
    }
}
