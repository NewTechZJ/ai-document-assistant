package com.demo.aidocumentassistant.document;

import com.demo.aidocumentassistant.common.error.ServerBusyException;
import com.demo.aidocumentassistant.document.DocumentPreview.ChunkPreview;
import com.demo.aidocumentassistant.document.DocumentPreview.ChunkStats;
import com.demo.aidocumentassistant.document.DocumentPreview.PagePreview;
import com.demo.aidocumentassistant.ingestion.Chunker;
import com.demo.aidocumentassistant.ingestion.ChunkingProperties;
import com.demo.aidocumentassistant.ingestion.PageText;
import com.demo.aidocumentassistant.ingestion.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real Chunker; the extractor is replaced by fixed pages so each test controls the text exactly. */
@ExtendWith(OutputCaptureExtension.class)
class DocumentPreviewServiceTest {

    private static final ChunkingProperties SETTINGS = new ChunkingProperties(100, 20, 30);
    /** Chunks up to 300 chars, so a chunk can be longer than the 120-code-point snippet. */
    private static final ChunkingProperties LARGE_CHUNKS = new ChunkingProperties(300, 20, 30);
    private static final byte[] ANY_BYTES = new byte[1234];
    private static final int MAX_CONCURRENT = 2;

    @Test
    void reportsCountsPerDocumentAndPerPage() {
        String longPage = "Sentence number one. ".repeat(12).strip(); // ~250 chars -> several chunks
        DocumentPreview preview = serviceReturning(
                new PageText(1, longPage), new PageText(2, ""), new PageText(3, "Short page."))
                .preview("report.pdf", ANY_BYTES, true);

        assertEquals("report.pdf", preview.fileName());
        assertEquals(1234, preview.fileSizeBytes());
        assertEquals(3, preview.pageCount());
        assertEquals(1, preview.blankPageCount());
        assertEquals(preview.chunks().size(), preview.chunkCount());
        assertFalse(preview.chunksTruncated());
        assertEquals(SETTINGS, preview.settings());

        List<PagePreview> pages = preview.pages();
        assertEquals(List.of(1, 2, 3), pages.stream().map(PagePreview::pageNumber).toList());
        assertTrue(pages.get(0).chunkCount() > 1);
        assertEquals(0, pages.get(1).chunkCount(), "blank page has no chunks");
        assertEquals(1, pages.get(2).chunkCount());
        assertEquals(longPage.length(), pages.get(0).charCount());
        assertEquals(preview.chunkCount(), pages.stream().mapToInt(PagePreview::chunkCount).sum());
    }

    @Test
    void chunkPreviewsMirrorTheChunks() {
        DocumentPreview preview = serviceReturning(new PageText(1, "One."), new PageText(2, "Two."))
                .preview("a.pdf", ANY_BYTES, true);

        assertEquals(2, preview.chunks().size());
        assertEquals(new ChunkPreview(0, 1, 4, "One."), preview.chunks().get(0));
        assertEquals(new ChunkPreview(1, 2, 4, "Two."), preview.chunks().get(1));
    }

    @Test
    void docIdIsAFreshPreviewId() {
        DocumentPreviewService service = serviceReturning(new PageText(1, "x"));

        String first = service.preview("a.pdf", ANY_BYTES, false).docId();
        String second = service.preview("a.pdf", ANY_BYTES, false).docId();

        assertTrue(first.startsWith("preview-"), first);
        assertNotEquals(first, second);
    }

    @Test
    void chunkStatsAreMinMaxAndRoundedAverage() {
        // Three one-chunk pages of 4, 5 and 6 chars -> avg 5; then 4 and 5 -> avg 4.5 rounds to 5.
        assertEquals(new ChunkStats(4, 6, 5), serviceReturning(
                new PageText(1, "aaaa"), new PageText(2, "bbbbb"), new PageText(3, "cccccc"))
                .preview("a.pdf", ANY_BYTES, false).chunkStats());
        assertEquals(new ChunkStats(4, 5, 5), serviceReturning(
                new PageText(1, "aaaa"), new PageText(2, "bbbbb"))
                .preview("a.pdf", ANY_BYTES, false).chunkStats());
    }

    // ---------------------------------------------------------------- chunk text: full vs snippet

    @Test
    void chunkTextIsASnippetByDefault() {
        String page = "a".repeat(250); // one 250-char chunk with LARGE_CHUNKS

        ChunkPreview chunk = serviceReturning(LARGE_CHUNKS, new PageText(1, page))
                .preview("a.pdf", ANY_BYTES, false).chunks().get(0);

        assertEquals("a".repeat(120) + "…", chunk.text());
        assertEquals(250, chunk.charCount(), "charCount is the real chunk length, not the snippet length");
    }

    @Test
    void chunkTextIsFullWhenRequested() {
        String page = "a".repeat(250);

        ChunkPreview chunk = serviceReturning(LARGE_CHUNKS, new PageText(1, page))
                .preview("a.pdf", ANY_BYTES, true).chunks().get(0);

        assertEquals(page, chunk.text());
        assertEquals(250, chunk.charCount());
    }

    @Test
    void shortChunkIsTheSameWithOrWithoutFullText() {
        DocumentPreviewService service = serviceReturning(new PageText(1, "Short."));

        assertEquals("Short.", service.preview("a.pdf", ANY_BYTES, false).chunks().get(0).text());
        assertEquals("Short.", service.preview("a.pdf", ANY_BYTES, true).chunks().get(0).text());
    }

    // ---------------------------------------------------------------- response cap

    @Test
    void chunkListIsCappedAt200ButCountsAndStatsCoverAllChunks() {
        // 201 one-chunk pages -> 201 chunks
        PageText[] pages = IntStream.rangeClosed(1, 201)
                .mapToObj(n -> new PageText(n, "Page " + n + "."))
                .toArray(PageText[]::new);

        DocumentPreview preview = serviceReturning(pages).preview("a.pdf", ANY_BYTES, true);

        assertEquals(201, preview.chunkCount());
        assertEquals(200, preview.chunks().size());
        assertTrue(preview.chunksTruncated());
        assertEquals(199, preview.chunks().get(199).index(), "the first 200 chunks are returned, in order");
        assertEquals("Page 201.".length(), preview.chunkStats().maxChars(), "stats include the dropped chunk");
        assertEquals(201, preview.pages().size(), "pages are never truncated");
    }

    @Test
    void exactly200ChunksAreNotTruncated() {
        PageText[] pages = IntStream.rangeClosed(1, 200)
                .mapToObj(n -> new PageText(n, "x"))
                .toArray(PageText[]::new);

        DocumentPreview preview = serviceReturning(pages).preview("a.pdf", ANY_BYTES, false);

        assertEquals(200, preview.chunks().size());
        assertFalse(preview.chunksTruncated());
    }

    // ---------------------------------------------------------------- snippet (pages)

    @Test
    void snippetOfShortPageIsTheWholeText() {
        String text = "a".repeat(120);
        assertEquals(text, snippetOf(text));
    }

    @Test
    void snippetIsCutAt120CodePointsWithEllipsis() {
        assertEquals("a".repeat(120) + "…", snippetOf("a".repeat(121)));
    }

    @Test
    void snippetNeverSplitsAnEmoji() {
        // 119 letters + emoji = 120 code points (121 chars), then more text.
        assertEquals("a".repeat(119) + "😀…", snippetOf("a".repeat(119) + "😀" + "b".repeat(5)));
    }

    @Test
    void emojiCountAsOneCodePointForTheLimit() {
        String text = "😀".repeat(120); // 240 chars but 120 code points
        assertEquals(text, snippetOf(text));
    }

    @Test
    void snippetDropsTrailingSpaceBeforeTheEllipsis() {
        assertEquals("a".repeat(119) + "…", snippetOf("a".repeat(119) + " " + "b".repeat(10)));
    }

    @Test
    void blankPageHasEmptySnippet() {
        assertEquals("", serviceReturning(new PageText(1, ""), new PageText(2, "x"))
                .preview("a.pdf", ANY_BYTES, false).pages().get(0).snippet());
    }

    // ---------------------------------------------------------------- concurrency limit

    @Test
    @Timeout(10)
    void rejectsWorkBeyondTheConcurrencyLimitAndFreesTheSlotAfterwards() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PdfTextExtractor blockingExtractor = new PdfTextExtractor() {
            @Override
            public List<PageText> extract(byte[] pdfBytes) {
                entered.countDown();
                await(release);
                return List.of(new PageText(1, "Done."));
            }
        };
        DocumentPreviewService service = new DocumentPreviewService(blockingExtractor, new Chunker(SETTINGS), SETTINGS, 1);

        CompletableFuture<DocumentPreview> first =
                CompletableFuture.supplyAsync(() -> service.preview("a.pdf", ANY_BYTES, false));
        entered.await(); // the first call now holds the only slot

        assertThrows(ServerBusyException.class, () -> service.preview("b.pdf", ANY_BYTES, false));

        release.countDown();
        assertEquals(1, first.get().chunkCount());
        assertEquals(1, service.preview("c.pdf", ANY_BYTES, false).chunkCount(), "slot is free again");
    }

    @Test
    void slotIsReleasedWhenExtractionFails() {
        PdfTextExtractor failingOnce = new PdfTextExtractor() {
            private boolean failed;

            @Override
            public List<PageText> extract(byte[] pdfBytes) {
                if (!failed) {
                    failed = true;
                    throw new IllegalStateException("boom");
                }
                return List.of(new PageText(1, "Ok."));
            }
        };
        DocumentPreviewService service = new DocumentPreviewService(failingOnce, new Chunker(SETTINGS), SETTINGS, 1);

        assertThrows(IllegalStateException.class, () -> service.preview("a.pdf", ANY_BYTES, false));
        assertEquals(1, service.preview("a.pdf", ANY_BYTES, false).chunkCount(), "failed call must free its slot");
    }

    // ---------------------------------------------------------------- logging

    @Test
    void logsCountsButNeverDocumentText(CapturedOutput output) {
        serviceReturning(new PageText(1, "Confidential salary table for 2026."))
                .preview("a.pdf", ANY_BYTES, true);

        assertTrue(output.getAll().contains("pages=1"), output.getAll());
        assertTrue(output.getAll().contains("chunks=1"), output.getAll());
        assertFalse(output.getAll().contains("Confidential"), "document text must not be logged");
    }

    // ---------------------------------------------------------------- helpers

    private static String snippetOf(String pageText) {
        return serviceReturning(new PageText(1, pageText)).preview("a.pdf", ANY_BYTES, false).pages().get(0).snippet();
    }

    private static DocumentPreviewService serviceReturning(PageText... pages) {
        return serviceReturning(SETTINGS, pages);
    }

    private static DocumentPreviewService serviceReturning(ChunkingProperties settings, PageText... pages) {
        PdfTextExtractor fixedExtractor = new PdfTextExtractor() {
            @Override
            public List<PageText> extract(byte[] pdfBytes) {
                return List.of(pages);
            }
        };
        return new DocumentPreviewService(fixedExtractor, new Chunker(settings), settings, MAX_CONCURRENT);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}