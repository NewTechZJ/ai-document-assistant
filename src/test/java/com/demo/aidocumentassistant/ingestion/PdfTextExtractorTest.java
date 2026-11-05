package com.demo.aidocumentassistant.ingestion;

import com.demo.aidocumentassistant.common.error.EncryptedPdfException;
import com.demo.aidocumentassistant.common.error.NoExtractableTextException;
import com.demo.aidocumentassistant.common.error.NotAPdfException;
import com.demo.aidocumentassistant.common.error.PdfExtractionException;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    // ---------------------------------------------------------------- happy path

    @Test
    void extractsOnePageTextPerPageWithOneBasedNumbers() {
        List<PageText> pages = extractor.extract(TestPdfs.withPages("First page.", "Second page.", "Third page."));

        assertEquals(List.of(
                new PageText(1, "First page."),
                new PageText(2, "Second page."),
                new PageText(3, "Third page.")), pages);
    }

    @Test
    void blankPagesAreKeptAsEmptyTextSoPageNumbersStayPositional() {
        List<PageText> pages = extractor.extract(TestPdfs.withPages("", "Only text.", ""));

        assertEquals(3, pages.size());
        assertEquals(new PageText(1, ""), pages.get(0));
        assertEquals(new PageText(2, "Only text."), pages.get(1));
        assertEquals(new PageText(3, ""), pages.get(2));
    }

    @Test
    void paragraphsBecomeBlankLinesAndWrappedLinesAreJoined() {
        // Regression: without stripper.setParagraphEnd("\n") every page became a single paragraph.
        byte[] pdf = TestPdfs.withParagraphs(List.of(
                List.of("The first paragraph is wrapped", "over two lines."),
                List.of("The second paragraph.")));

        String text = extractor.extract(pdf).get(0).text();

        assertEquals("The first paragraph is wrapped over two lines.\n\nThe second paragraph.", text);
    }

    @Test
    void resultIsImmutable() {
        List<PageText> pages = extractor.extract(TestPdfs.withPages("x"));

        assertThrows(UnsupportedOperationException.class, () -> pages.add(new PageText(2, "")));
    }

    @Test
    void ownerPasswordOnlyPdfWithExtractionAllowedIsRead() {
        List<PageText> pages = extractor.extract(TestPdfs.ownerPasswordOnly("Readable.", true));

        assertEquals("Readable.", pages.get(0).text());
    }

    @Test
    void smallJunkBeforeTheHeaderIsTolerated() {
        // Checks that PDFBox itself loads such a file; otherwise the 1024-byte header search promises too much.
        byte[] junk = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '\n'}; // UTF-8 BOM + newline
        byte[] pdf = concat(junk, TestPdfs.withPages("After junk."));

        assertEquals("After junk.", extractor.extract(pdf).get(0).text());
    }

    // ---------------------------------------------------------------- not a PDF

    @Test
    void nullOrEmptyBytesAreNotAPdf() {
        assertThrows(NotAPdfException.class, () -> extractor.extract(null));
        assertThrows(NotAPdfException.class, () -> extractor.extract(new byte[0]));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "%PDF", "hello", "PK\u0003\u0004 a zip file", "<html>not a pdf</html>"})
    void bytesWithoutTheMagicHeaderAreNotAPdf(String content) {
        assertThrows(NotAPdfException.class, () -> extractor.extract(content.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void headerEndingExactlyAtTheEndOfTheSearchWindowIsFound() {
        // "%PDF-" at offset 1019 occupies bytes 1019..1023, the last 5 bytes of the 1024-byte window.
        byte[] bytes = headerAt(1019);

        PdfExtractionException e = assertThrows(PdfExtractionException.class, () -> extractor.extract(bytes));
        assertFalse(e instanceof NotAPdfException, "header was found, so the failure must come from parsing");
    }

    @Test
    void headerStartingAfterTheSearchWindowIsNotFound() {
        assertThrows(NotAPdfException.class, () -> extractor.extract(headerAt(1020)));
    }

    // ---------------------------------------------------------------- unreadable

    @Test
    void headerFollowedByGarbageIsUnreadable() {
        byte[] bytes = "%PDF-1.7\nthis is not a real pdf body".getBytes(StandardCharsets.US_ASCII);

        PdfExtractionException e = assertThrows(PdfExtractionException.class, () -> extractor.extract(bytes));
        assertFalse(e instanceof NotAPdfException);
        assertFalse(e instanceof EncryptedPdfException);
    }

    // ---------------------------------------------------------------- encrypted

    @Test
    void userPasswordProtectedPdfIsEncrypted() {
        EncryptedPdfException e = assertThrows(EncryptedPdfException.class,
                () -> extractor.extract(TestPdfs.userPasswordProtected("Secret.")));

        assertInstanceOf(InvalidPasswordException.class, e.getCause());
        assertTrue(e.getMessage().contains("password"));
    }

    @Test
    void ownerPasswordOnlyPdfWithExtractionDeniedIsEncrypted() {
        EncryptedPdfException e = assertThrows(EncryptedPdfException.class,
                () -> extractor.extract(TestPdfs.ownerPasswordOnly("Locked.", false)));

        assertTrue(e.getMessage().contains("permissions"));
    }

    // ---------------------------------------------------------------- scanned

    @Test
    void pdfWithoutAnyTextLooksScanned() {
        NoExtractableTextException e = assertThrows(NoExtractableTextException.class,
                () -> extractor.extract(TestPdfs.withPages("", "")));

        assertTrue(e.getMessage().contains("2 page(s)"), e.getMessage());
    }

    @Test
    void pageWithOnlyPunctuationCountsAsNoText() {
        assertThrows(NoExtractableTextException.class, () -> extractor.extract(TestPdfs.withPages("- - - . . .")));
    }

    @Test
    void oneDigitIsEnoughToCountAsText() {
        assertEquals("7", extractor.extract(TestPdfs.withPages("", "7")).get(1).text());
    }

    // ---------------------------------------------------------------- helpers

    private static byte[] headerAt(int offset) {
        byte[] junk = new byte[offset];
        Arrays.fill(junk, (byte) 'x');
        return concat(junk, "%PDF-1.7\ngarbage".getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
