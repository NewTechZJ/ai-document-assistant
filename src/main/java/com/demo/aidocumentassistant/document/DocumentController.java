package com.demo.aidocumentassistant.document;

import com.demo.aidocumentassistant.common.error.BadRequestException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private static final String DEFAULT_FILE_NAME = "upload.pdf";

    private final DocumentPreviewService documentPreviewService;

    public DocumentController(DocumentPreviewService documentPreviewService) {
        this.documentPreviewService = documentPreviewService;
    }

    /** Dev/debug only: shows how a PDF would be chunked without storing anything. Real uploads go to S3 via presigned URL. */
    @Profile("local")
    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentPreview preview(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean includeChunkText) {
        if (file.isEmpty()) {
            throw new BadRequestException("The uploaded file is empty.");
        }
        return documentPreviewService.preview(
                safeFileName(file.getOriginalFilename()),
                readBytes(file),
                includeChunkText);
    }

    /**
     * Keeps only the last path segment: some browsers send a full client path ("C:\\Users\\me\\a.pdf").
     * The name is only echoed back for display; it is never used to build a file path.
     */
    private static String safeFileName(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return DEFAULT_FILE_NAME;
        }
        int lastSeparator = Math.max(originalFileName.lastIndexOf('/'), originalFileName.lastIndexOf('\\'));
        String name = originalFileName.substring(lastSeparator + 1).strip();
        return name.isEmpty() ? DEFAULT_FILE_NAME : name;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }
}
