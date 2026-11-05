package com.demo.aidocumentassistant.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TextNormalizerTest {

    // ---------------------------------------------------------------- empty input

    @Test
    void nullAndEmptyBecomeEmpty() {
        assertEquals("", TextNormalizer.normalize(null));
        assertEquals("", TextNormalizer.normalize(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\n", "\n\n\n", " \t \n \u00A0 ", "\u00AD\u200B\uFEFF"})
    void whitespaceOrInvisibleOnlyBecomesEmpty(String raw) {
        assertEquals("", TextNormalizer.normalize(raw));
    }

    // ---------------------------------------------------------------- character cleanup

    @Test
    void ligaturesAndFullWidthFormsBecomePlainLetters() {
        assertEquals("file flow", TextNormalizer.normalize("\uFB01le \uFB02ow"));
        assertEquals("ABC123", TextNormalizer.normalize("ＡＢＣ１２３"));
    }

    @Test
    void invisibleCharactersAreRemoved() {
        // soft hyphen, zero-width space, word joiner, BOM
        assertEquals("extraction", TextNormalizer.normalize("ex\u00ADtrac\u200Bti\u2060on\uFEFF"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
            "Persian ZWNJ,     \u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645",
            "Hindi ZWNJ,       \u0915\u094D\u200C\u0937",
            "Hindi ZWJ,        \u0930\u094D\u200D\u092F",
            "Emoji ZWJ,        \uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67",
    })
    void zeroWidthJoinersAreKept(String label, String text) {
        assertEquals(text, TextNormalizer.normalize(text), label);
    }

    @Test
    void controlCharactersBecomeSpaces() {
        assertEquals("a b c", TextNormalizer.normalize("a\u0000b\u0007c"));
    }

    @Test
    void tabsAndUnicodeSpacesCollapseToOneSpace() {
        assertEquals("a b c d", TextNormalizer.normalize("a\t\tb\u00A0\u00A0c \u2003 d"));
    }

    @Test
    void windowsAndOldMacLineEndingsCountAsLineBreaks() {
        assertEquals("one two\n\nthree", TextNormalizer.normalize("one\r\ntwo\r\n\r\nthree"));
        assertEquals("one two\n\nthree", TextNormalizer.normalize("one\rtwo\r\rthree"));
    }

    // ---------------------------------------------------------------- hyphenation

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "extrac-\\ntion          | extraction",
            "extrac\u2010\\ntion     | extraction",      // U+2010 HYPHEN
            "Über-\\nsicht           | Übersicht",       // non-ASCII letters
            "self-\\nservice         | selfservice",     // accepted trade-off: real compounds are joined too
            "COVID-\\n19             | COVID- 19",       // digit after the break: kept
            "Spring-\\nBoot          | Spring- Boot",    // uppercase after the break: kept
            "well -\\nknown          | well - known",    // hyphen not attached to a letter: kept
            "re-\\n\\nstart          | re-\\n\\nstart",    // paragraph break, not a line wrap: kept
    })
    void hyphenatedLineBreaksAreRepairedOnlyForWrappedWords(String raw, String expected) {
        assertEquals(expected.replace("\\n", "\n"), TextNormalizer.normalize(raw.replace("\\n", "\n")));
    }

    @Test
    void hyphenationIsRepairedEvenWithSpacesAroundTheLineBreak() {
        assertEquals("extraction", TextNormalizer.normalize("extrac-   \n   tion"));
    }

    // ---------------------------------------------------------------- paragraphs

    @Test
    void singleLineBreaksAreUnwrappedIntoSpaces() {
        assertEquals("a line wrapped by the PDF", TextNormalizer.normalize("a line\nwrapped by\nthe PDF"));
    }

    @Test
    void blankLinesSeparateParagraphsByExactlyOneBlankLine() {
        assertEquals("first\n\nsecond\n\nthird",
                TextNormalizer.normalize("\n\nfirst\n\n\n\n\nsecond\n  \n \t \nthird\n\n"));
    }

    @Test
    void outputHasNoLeadingOrTrailingWhitespace() {
        assertEquals("text", TextNormalizer.normalize("  \n\n  text  \n\n  "));
    }

    @Test
    void alreadyNormalTextIsUnchanged() {
        String normal = "First paragraph. Second sentence.\n\nSecond paragraph.";
        assertEquals(normal, TextNormalizer.normalize(normal));
    }

    @Test
    void normalizingTwiceChangesNothing() {
        Random random = new Random(11);
        for (int run = 0; run < 1_000; run++) {
            String once = TextNormalizer.normalize(randomRaw(random));
            assertEquals(once, TextNormalizer.normalize(once));
        }
    }

    /** The contract Chunker relies on (see TextNormalizer's javadoc), checked on random messy input. */
    @Test
    void outputContractHoldsForRandomInput() {
        Random random = new Random(5);
        for (int run = 0; run < 2_000; run++) {
            String raw = randomRaw(random);
            String out = TextNormalizer.normalize(raw);
            String where = " for input [" + raw.replace("\n", "\\n").replace("\r", "\\r") + "]";

            assertEquals(out.strip(), out, "no leading/trailing whitespace" + where);
            assertFalse(out.contains("\n\n\n"), "at most one blank line between paragraphs" + where);
            assertFalse(out.contains("  "), "no double spaces" + where);
            assertFalse(out.contains("\r") || out.contains("\t"), "no CR or tab" + where);
            for (String paragraph : out.split("\n\n", -1)) {
                assertFalse(paragraph.contains("\n"), "no newline inside a paragraph" + where);
                assertEquals(paragraph.strip(), paragraph, "paragraphs are trimmed" + where);
                if (!out.isEmpty()) {
                    assertFalse(paragraph.isEmpty(), "no empty paragraphs" + where);
                }
            }
        }
    }

    private static String randomRaw(Random random) {
        String[] alphabet = {"a", "b", "Z", "é", "1", "-", ".", " ", " ", "\t", "\n", "\n", "\r\n", "\r",
                "\u00A0", "\u00AD", "\u200B", "\uFB01", "\u0007", "\u2010"};
        StringBuilder raw = new StringBuilder();
        int length = random.nextInt(60);
        for (int i = 0; i < length; i++) {
            raw.append(alphabet[random.nextInt(alphabet.length)]);
        }
        return raw.toString();
    }
}
