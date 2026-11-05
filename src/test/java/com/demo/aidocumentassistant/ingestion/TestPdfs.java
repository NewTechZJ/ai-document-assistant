package com.demo.aidocumentassistant.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Builds small PDFs in memory, so tests need no binary fixtures.
 * Public because the document/ tests use it too.
 * <p>
 * A page is a list of paragraphs; a paragraph is a list of lines. Lines are 14pt apart and paragraphs
 * are separated by a 40pt gap, which PDFTextStripper reports as a paragraph break.
 * An empty paragraph list gives a page with no text (a filled rectangle, like a scanned image).
 */
public final class TestPdfs {

    private static final float FONT_SIZE = 12;
    private static final float LINE_HEIGHT = 14;
    private static final float PARAGRAPH_GAP = 40;

    private TestPdfs() {
    }

    /** One page per string, each a single line. "" gives a page without text. */
    public static byte[] withPages(String... lines) {
        return build(null, java.util.Arrays.stream(lines)
                .map(line -> line.isEmpty() ? List.<List<String>>of() : List.of(List.of(line)))
                .toList());
    }

    /** One page with the given paragraphs, each a list of lines. */
    public static byte[] withParagraphs(List<List<String>> paragraphs) {
        return build(null, List.of(paragraphs));
    }

    /** Needs a user password to open at all. */
    public static byte[] userPasswordProtected(String text) {
        return build(new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission()),
                List.of(List.of(List.of(text))));
    }

    /** Opens without a password (owner password only); text extraction allowed or not. */
    public static byte[] ownerPasswordOnly(String text, boolean canExtractContent) {
        AccessPermission permission = new AccessPermission();
        permission.setCanExtractContent(canExtractContent);
        return build(new StandardProtectionPolicy("owner-secret", "", permission), List.of(List.of(List.of(text))));
    }

    private static byte[] build(StandardProtectionPolicy protection, List<List<List<String>>> pages) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (List<List<String>> paragraphs : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    if (paragraphs.isEmpty()) {
                        content.addRect(72, 500, 200, 200); // "image" area, no text
                        content.fill();
                        continue;
                    }
                    float y = 720;
                    for (List<String> paragraph : paragraphs) {
                        for (String line : paragraph) {
                            content.beginText();
                            content.setFont(font, FONT_SIZE);
                            content.newLineAtOffset(72, y);
                            content.showText(line);
                            content.endText();
                            y -= LINE_HEIGHT;
                        }
                        y -= PARAGRAPH_GAP;
                    }
                }
            }
            if (protection != null) {
                protection.setEncryptionKeyLength(128);
                document.protect(protection);
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
