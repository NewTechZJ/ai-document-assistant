package com.demo.aidocumentassistant.document;

import com.demo.aidocumentassistant.document.DocumentPreview.ChunkStats;
import com.demo.aidocumentassistant.document.DocumentPreview.PagePreview;
import com.demo.aidocumentassistant.ingestion.Chunker;
import com.demo.aidocumentassistant.ingestion.ChunkingProperties;
import com.demo.aidocumentassistant.ingestion.PageText;
import com.demo.aidocumentassistant.ingestion.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real Chunker; the extractor is replaced by fixed pages so each test controls the text exactly. */
@ExtendWith(OutputCaptureExtension.class)
class DocumentPreviewServiceTest {

    private static final ChunkingProperties SETTINGS = new ChunkingProperties(100, 20, 30);
    private static final byte[] ANY_BYTES = new byte[1234];

    @Test
    void reportsCountsPerDocumentAndPerPage() {
        String longPage = "Sentence number one. ".repeat(12).strip(); // ~250 chars -> several chunks
        DocumentPreview preview = serviceReturning(
                new PageText(1, longPage), new PageText(2, ""), new PageText(3, "Short page."))
                .preview("report.pdf", ANY_BYTES);

        assertEquals("report.pdf", preview.fileName());
        assertEquals(1234, preview.fileSizeBytes());
        assertEquals(3, preview.pageCount());
        assertEquals(1, preview.blankPageCount());
        assertEquals(preview.chunks().size(), preview.chunkCount());
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
                .preview("a.pdf", ANY_BYTES);

        assertEquals(2, preview.chunks().size());
        assertEquals(new DocumentPreview.ChunkPreview(0, 1, 4, "One."), preview.chunks().get(0));
        assertEquals(new DocumentPreview.ChunkPreview(1, 2, 4, "Two."), preview.chunks().get(1));
    }

    @Test
    void docIdIsAFreshPreviewId() {
        DocumentPreviewService service = serviceReturning(new PageText(1, "x"));

        String first = service.preview("a.pdf", ANY_BYTES).docId();
        String second = service.preview("a.pdf", ANY_BYTES).docId();

        assertTrue(first.startsWith("preview-"), first);
        assertNotEquals(first, second);
    }

    @Test
    void chunkStatsAreMinMaxAndRoundedAverage() {
        // Three one-chunk pages of 4, 5 and 6 chars -> avg 5; then 4 and 5 -> avg 4.5 rounds to 5.
        assertEquals(new ChunkStats(4, 6, 5), serviceReturning(
                new PageText(1, "aaaa"), new PageText(2, "bbbbb"), new PageText(3, "cccccc"))
                .preview("a.pdf", ANY_BYTES).chunkStats());
        assertEquals(new ChunkStats(4, 5, 5), serviceReturning(
                new PageText(1, "aaaa"), new PageText(2, "bbbbb"))
                .preview("a.pdf", ANY_BYTES).chunkStats());
    }

    // ---------------------------------------------------------------- snippet

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
                .preview("a.pdf", ANY_BYTES).pages().get(0).snippet());
    }

    // ---------------------------------------------------------------- logging

    @Test
    void logsCountsButNeverDocumentText(CapturedOutput output) {
        serviceReturning(new PageText(1, "Confidential salary table for 2026."))
                .preview("a.pdf", ANY_BYTES);

        assertTrue(output.getAll().contains("pages=1"), output.getAll());
        assertTrue(output.getAll().contains("chunks=1"), output.getAll());
        assertFalse(output.getAll().contains("Confidential"), "document text must not be logged");
    }

    // ---------------------------------------------------------------- helpers

    private static String snippetOf(String pageText) {
        return serviceReturning(new PageText(1, pageText)).preview("a.pdf", ANY_BYTES).pages().get(0).snippet();
    }

    private static DocumentPreviewService serviceReturning(PageText... pages) {
        PdfTextExtractor fixedExtractor = new PdfTextExtractor() {
            @Override
            public List<PageText> extract(byte[] pdfBytes) {
                return List.of(pages);
            }
        };
        return new DocumentPreviewService(fixedExtractor, new Chunker(SETTINGS), SETTINGS);
    }
}
