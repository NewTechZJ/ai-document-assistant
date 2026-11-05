package com.demo.aidocumentassistant.ingestion;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Small settings (target 100 / overlap 20 / min 30) keep the test texts short and readable.
 * The rules do not depend on the absolute numbers, only on target, overlap and min.
 */
class ChunkerTest {

    private static final int TARGET = 100;
    private static final int OVERLAP = 20;
    private static final int MIN = 30;

    private final Chunker chunker = new Chunker(new ChunkingProperties(TARGET, OVERLAP, MIN));

    // ---------------------------------------------------------------- basics

    @Test
    void shortPageBecomesExactlyOneChunkEqualToThePage() {
        // Regression: the last chunk of a page used to be dropped, so a short page produced 0 chunks.
        List<Chunk> chunks = chunker.chunk("doc", List.of(new PageText(1, "Hello world.")));

        assertEquals(1, chunks.size());
        assertEquals(new Chunk("doc", 0, 1, "Hello world."), chunks.get(0));
    }

    @Test
    void paragraphOfExactlyTargetCharsStaysOneChunk() {
        String page = "a".repeat(TARGET);

        List<Chunk> chunks = chunker.chunk("doc", List.of(new PageText(1, page)));

        assertEquals(List.of(page), texts(chunks));
    }

    @Test
    void emptyPageListGivesNoChunks() {
        assertTrue(chunker.chunk("doc", List.of()).isEmpty());
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> chunker.chunk(null, List.of()));
        assertThrows(NullPointerException.class, () -> chunker.chunk("doc", null));
    }

    @Test
    void resultIsImmutable() {
        List<Chunk> chunks = chunker.chunk("doc", List.of(new PageText(1, "Hello.")));

        assertThrows(UnsupportedOperationException.class, () -> chunks.add(chunks.get(0)));
    }

    @Test
    void sameInputGivesSameOutput() {
        List<PageText> pages = List.of(new PageText(1, words(60)), new PageText(2, words(30)));

        assertEquals(chunker.chunk("doc", pages), chunker.chunk("doc", pages));
    }

    // ---------------------------------------------------------------- pages and indexes

    @Test
    void indexesAreContinuousAcrossPagesAndBlankPagesKeepTheirNumbers() {
        List<PageText> pages = List.of(
                new PageText(1, words(60)),
                new PageText(2, ""),
                new PageText(3, "   "),
                new PageText(4, "Page four."));

        List<Chunk> chunks = chunker.chunk("doc", pages);

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).index(), "index of chunk " + i);
            assertEquals("doc", chunks.get(i).docId());
        }
        assertTrue(chunks.stream().noneMatch(c -> c.pageNumber() == 2 || c.pageNumber() == 3),
                "blank pages produce no chunks");
        Chunk last = chunks.get(chunks.size() - 1);
        assertEquals(4, last.pageNumber(), "page 4 keeps its real number");
        assertEquals("Page four.", last.text());
    }

    @Test
    void aChunkNeverSpansTwoPages() {
        String page1 = words(40);
        String page2 = words(40).replace("w", "v");

        for (Chunk chunk : chunker.chunk("doc", List.of(new PageText(1, page1), new PageText(2, page2)))) {
            String pageText = chunk.pageNumber() == 1 ? page1 : page2;
            assertTrue(pageText.contains(chunk.text()), "chunk " + chunk.index() + " must come from its own page");
        }
    }

    // ---------------------------------------------------------------- size rules

    @Test
    void chunksRespectTheSizeLimitOnRandomPages() {
        // Every chunk is <= TARGET, except the last chunk of a page, which may have a short tail merged in.
        // The merge adds the piece separator ("\n\n" = 2 chars when the tail starts a paragraph) plus fewer than
        // MIN chars of new text, so the real limit is TARGET + 2 + (MIN - 1).
        // NOTE: Chunker's javadoc says "at most targetChars + minChars - 1"; that is 2 chars too low (a page
        // producing 131 with these settings was found). Fix the javadoc (or the code) and keep this test.
        int mergedLimit = TARGET + TextNormalizer.PARAGRAPH_SEPARATOR.length() + MIN - 1;
        Random random = new Random(42);

        for (int run = 0; run < 2_000; run++) {
            String page = randomPage(random);
            List<Chunk> chunks = chunker.chunk("doc", List.of(new PageText(1, page)));

            for (int i = 0; i < chunks.size(); i++) {
                int limit = i == chunks.size() - 1 ? mergedLimit : TARGET;
                assertTrue(chunks.get(i).charCount() <= limit,
                        "chunk " + i + " has " + chunks.get(i).charCount() + " chars (limit " + limit + ") for page: "
                                + page.replace("\n", "\\n"));
            }
        }
    }

    @Test
    void everyChunkIsAnExactSubstringOfItsPageForSpaceSeparatedText() {
        // Citations and highlighting rely on finding the chunk text in the page text.
        Random random = new Random(7);

        for (int run = 0; run < 2_000; run++) {
            String page = randomPage(random);
            for (Chunk chunk : chunker.chunk("doc", List.of(new PageText(1, page)))) {
                assertTrue(page.contains(chunk.text()),
                        "not a substring: [" + chunk.text().replace("\n", "\\n") + "] of page ["
                                + page.replace("\n", "\\n") + "]");
            }
        }
    }

    @Test
    void chunksNeverStartOrEndWithWhitespace() {
        Random random = new Random(3);
        for (int run = 0; run < 500; run++) {
            for (Chunk chunk : chunker.chunk("doc", List.of(new PageText(1, randomPage(random))))) {
                assertEquals(chunk.text().strip(), chunk.text());
                assertFalse(chunk.text().isEmpty());
            }
        }
    }

    // ---------------------------------------------------------------- merge rule (minChars)

    @Test
    void shortTailIsMergedIntoThePreviousChunk() {
        String first = "a".repeat(98);
        String tail = "b".repeat(5); // fewer than MIN new chars

        List<Chunk> chunks = chunker.chunk("doc", List.of(new PageText(1, first + "\n\n" + tail)));

        assertEquals(List.of(first + "\n\n" + tail), texts(chunks));
    }

    @Test
    void tailWithAtLeastMinCharsBecomesItsOwnChunk() {
        String first = "a".repeat(98);
        String tail = "b".repeat(MIN);

        List<Chunk> chunks = chunker.chunk("doc", List.of(new PageText(1, first + "\n\n" + tail)));

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(1).text().endsWith(tail));
    }

    @Test
    void minCharsZeroNeverMerges() {
        Chunker noMerge = new Chunker(new ChunkingProperties(TARGET, OVERLAP, 0));
        String first = "a".repeat(98);

        List<Chunk> chunks = noMerge.chunk("doc", List.of(new PageText(1, first + "\n\n" + "b".repeat(5))));

        assertEquals(List.of(first, "bbbbb"), texts(chunks));
    }

    @Test
    void firstChunkOfAPageIsNeverMergedAway() {
        // Nothing before it to merge into, and it must not be merged into the previous page's chunk.
        List<Chunk> chunks = chunker.chunk("doc", List.of(new PageText(1, words(40)), new PageText(2, "Tiny.")));

        Chunk last = chunks.get(chunks.size() - 1);
        assertEquals(2, last.pageNumber());
        assertEquals("Tiny.", last.text());
    }

    // ---------------------------------------------------------------- overlap

    @Test
    void nextChunkStartsWithAWordAlignedTailOfThePreviousChunk() {
        List<Chunk> chunks = chunker.chunk("doc", List.of(new PageText(1, words(80))));
        assertTrue(chunks.size() >= 3, "needs several chunks, got " + chunks.size());

        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1).text();
            String firstWord = chunks.get(i).text().split(" ")[0];
            assertTrue(previous.endsWith(" " + firstWord) || previous.contains(" " + firstWord + " "),
                    "chunk " + i + " should start with a whole word from the previous chunk, got " + firstWord);
        }
    }

    @Test
    void overlapIsAtMostOverlapChars() {
        List<Chunk> chunks = chunker.chunk("doc", List.of(new PageText(1, words(80))));
        assertTrue(chunks.size() >= 3, "needs several chunks, got " + chunks.size());

        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1).text();
            String current = chunks.get(i).text();
            int shared = longestSuffixPrefix(previous, current);
            assertTrue(shared > 0, "chunk " + i + " should overlap the previous one");
            assertTrue(shared <= OVERLAP, "overlap of chunk " + i + " is " + shared);
        }
    }

    @Test
    void overlapZeroMeansNoRepeatedText() {
        Chunker noOverlap = new Chunker(new ChunkingProperties(TARGET, 0, 0));
        String page = words(40);

        List<Chunk> chunks = noOverlap.chunk("doc", List.of(new PageText(1, page)));

        assertEquals(page, String.join(" ", texts(chunks)));
    }

    @Test
    void overlapIsDroppedWhenTheNextPieceAlmostFillsAChunk() {
        String first = "Short first paragraph.";
        String big = "b".repeat(TARGET - 2); // no room for any overlap

        List<Chunk> chunks = chunker.chunk("doc", List.of(new PageText(1, first + "\n\n" + big)));

        assertEquals(List.of(first, big), texts(chunks));
    }

    // ---------------------------------------------------------------- hard splits

    @Test
    void longTokenWithoutBreaksIsCutIntoPartsThatLeaveRoomForOverlap() {
        String token = "x".repeat(5_000);

        List<Chunk> chunks = chunker.chunk("doc", List.of(new PageText(1, token)));

        assertTrue(chunks.size() > 1);
        for (Chunk chunk : chunks) {
            assertTrue(chunk.charCount() <= TARGET - OVERLAP || chunk.index() == chunks.size() - 1,
                    "chunk " + chunk.index() + " has " + chunk.charCount());
        }
        assertEquals(token, String.join("", texts(chunks)), "no overlap possible inside one token, so parts join back");
    }

    @Test
    void surrogatePairsAreNeverSplit() {
        String[] pages = {
                "x".repeat(79) + "😀" + "y".repeat(300),                         // emoji right at the hard-cut limit
                "😀".repeat(200),                                                  // only emoji
                "https://example.com/" + "a".repeat(59) + "😀" + "b".repeat(200),
        };
        for (String page : pages) {
            for (Chunk chunk : chunker.chunk("doc", List.of(new PageText(1, page)))) {
                assertTrue(hasNoBrokenSurrogates(chunk.text()), "broken surrogate in chunk " + chunk.index());
            }
        }
    }

    @Test
    void longSentenceWithSpacesIsCutAtWhitespace() {
        String page = words(60).replace(".", ""); // one long "sentence"

        for (Chunk chunk : chunker.chunk("doc", List.of(new PageText(1, page)))) {
            assertTrue(page.contains(chunk.text()));
            assertTrue(page.startsWith(chunk.text()) || page.contains(" " + chunk.text()),
                    "chunk starts at a word boundary: " + chunk.text());
        }
    }

    @Test
    void emojiSequencesAndIndicJoinersAreNeverSplit() {
        String family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"; // 👨‍👩‍👧
        String hindi = "\u0915\u094D\u200C\u0937";                          // क्‌ष
        List<String> pages = new ArrayList<>();
        for (int pad = 0; pad < 12; pad++) {
            pages.add("x".repeat(pad) + family.repeat(60)); // shift the cut through every position
        }
        pages.add(hindi.repeat(60));

        for (String page : pages) {
            for (Chunk chunk : chunker.chunk("doc", List.of(new PageText(1, page)))) {
                String text = chunk.text();
                char first = text.charAt(0);
                char last = text.charAt(text.length() - 1);
                assertTrue(page.contains(text));
                assertFalse(Character.isLowSurrogate(first) || Character.isHighSurrogate(last), "split emoji: " + text);
                assertFalse(first == '\u200D' || last == '\u200D' || first == '\u200C' || last == '\u200C',
                        "split at a joiner: " + text);
            }
        }
    }

    // ---------------------------------------------------------------- known bugs (enable once fixed)

    @Test
    @Disabled("Known bug: hard-split parts of a long URL are re-joined with \" \", e.g. \"/segment/ /segment/\"")
    void longUrlChunksAreExactSubstrings() {
        String url = "https://example.com/docs/" + "segment/".repeat(40);

        for (Chunk chunk : chunker.chunk("doc", List.of(new PageText(1, url)))) {
            assertTrue(url.contains(chunk.text()), "not a substring: " + chunk.text());
        }
    }

    @Test
    @Disabled("Known bug: sentences of a long CJK paragraph are re-joined with \" \", which the page never had")
    void chineseParagraphChunksAreExactSubstrings() {
        String page = "这是第一句话，内容比较长一些。".repeat(10);

        for (Chunk chunk : chunker.chunk("doc", List.of(new PageText(1, page)))) {
            assertTrue(page.contains(chunk.text()), "not a substring: " + chunk.text());
        }
    }

    // ---------------------------------------------------------------- helpers

    /** "w0 w1. w2 w3. ..." : short words with a sentence end every other word. */
    private static String words(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                text.append(' ');
            }
            text.append('w').append(i);
            if (i % 2 == 1) {
                text.append('.');
            }
        }
        return text.toString();
    }

    /** Normalized-looking page: 1-4 paragraphs of random words and sentences, up to ~120 chars each. */
    private static String randomPage(Random random) {
        List<String> paragraphs = new ArrayList<>();
        int paragraphCount = 1 + random.nextInt(4);
        for (int p = 0; p < paragraphCount; p++) {
            int length = 1 + random.nextInt(120);
            StringBuilder paragraph = new StringBuilder();
            while (paragraph.length() < length) {
                if (!paragraph.isEmpty()) {
                    paragraph.append(random.nextInt(3) == 0 ? ". " : " ");
                }
                paragraph.append("w".repeat(1 + random.nextInt(8)));
            }
            paragraphs.add(paragraph.toString());
        }
        return String.join(TextNormalizer.PARAGRAPH_SEPARATOR, paragraphs);
    }

    private static List<String> texts(List<Chunk> chunks) {
        return chunks.stream().map(Chunk::text).toList();
    }

    private static int longestSuffixPrefix(String previous, String current) {
        for (int length = Math.min(previous.length(), current.length()); length > 0; length--) {
            if (previous.endsWith(current.substring(0, length))) {
                return length;
            }
        }
        return 0;
    }

    private static boolean hasNoBrokenSurrogates(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) && (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1)))) {
                return false;
            }
            if (Character.isLowSurrogate(c) && (i == 0 || !Character.isHighSurrogate(text.charAt(i - 1)))) {
                return false;
            }
        }
        return true;
    }
}
