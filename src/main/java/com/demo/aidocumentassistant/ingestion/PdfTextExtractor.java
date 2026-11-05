package com.demo.aidocumentassistant.ingestion;

import com.demo.aidocumentassistant.common.error.EncryptedPdfException;
import com.demo.aidocumentassistant.common.error.NoExtractableTextException;
import com.demo.aidocumentassistant.common.error.NotAPdfException;
import com.demo.aidocumentassistant.common.error.PdfExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns PDF bytes into one {@link PageText} per page.
 * <p>
 * Plain Java on purpose (no Spring annotations): the same class will later run inside
 * the S3-event Lambda, and unit tests can just call {@code new PdfTextExtractor()}.
 * The bean is registered in {@link IngestionConfig}.
 */
public class PdfTextExtractor {

    /** The PDF spec lets readers tolerate junk before the header; Acrobat looks in the first 1024 bytes. */
    private static final int HEADER_SEARCH_WINDOW = 1024;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    public List<PageText> extract(byte[] pdfBytes) {
        requirePdfHeader(pdfBytes);

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() == 0) {
                throw new PdfExtractionException("The PDF has no pages; it may be corrupt.");
            }
            // PDFBox opens with the empty user password. An encrypted file that opens is "owner-password only":
            // readable, but the author may still have switched off text extraction.
            if (document.isEncrypted() && !document.getCurrentAccessPermission().canExtractContent()) {
                throw new EncryptedPdfException("The PDF's permissions do not allow text extraction.");
            }
            List<PageText> pages = extractPages(document);
            boolean hasAnyText = pages.stream().anyMatch(PdfTextExtractor::hasLetterOrDigit);
            if (!hasAnyText) {
                throw new NoExtractableTextException(
                        "The PDF has " + pages.size() + " page(s) but no extractable text. "
                                + "It is probably a scanned document; run OCR on it first.");
            }
            return List.copyOf(pages);

        } catch (InvalidPasswordException e) {
            // Must come before IOException: InvalidPasswordException is a subclass of it.
            throw new EncryptedPdfException("The PDF is password-protected.", e);
        } catch (IOException e) {
            throw new PdfExtractionException("The PDF could not be read; it may be corrupt or truncated.", e);
        }
    }

    /** One stripper call per page, so every PageText carries its exact 1-based page number. */
    private List<PageText> extractPages(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setLineSeparator("\n");
        stripper.setParagraphEnd("\n"); // line end + paragraph end = "\n\n", which TextNormalizer reads as a paragraph break

        int pageCount = document.getNumberOfPages();
        List<PageText> pages = new ArrayList<>(pageCount);
        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            String rawPageText = stripper.getText(document);
            pages.add(new PageText(pageNumber, TextNormalizer.normalize(rawPageText)));
        }
        return pages;
    }

    /**
     * Checking a few bytes is cheaper and gives a clearer message than letting the parser fail.
     * Searches for "%PDF-" anywhere in the first 1024 bytes (not only at offset 0),
     * because some generators write a few bytes of junk or a BOM before the header.
     */
    private static void requirePdfHeader(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new NotAPdfException("The file is empty.");
        }
        int lastPossibleStart = Math.min(bytes.length, HEADER_SEARCH_WINDOW) - PDF_MAGIC.length;
        for (int start = 0; start <= lastPossibleStart; start++) {
            if (pdfMagicStartsAt(bytes, start)) {
                return;
            }
        }
        throw new NotAPdfException("The file is not a PDF (no %PDF- header found).");
    }

    private static boolean pdfMagicStartsAt(byte[] bytes, int start) {
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[start + i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasLetterOrDigit(PageText page) {
        return page.text().codePoints().anyMatch(Character::isLetterOrDigit);
    }
}
