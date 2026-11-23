/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.document;

import com.demo.aidocumentassistant.common.error.EncryptedPdfException;
import com.demo.aidocumentassistant.common.error.NoExtractableTextException;
import com.demo.aidocumentassistant.common.error.NotAPdfException;
import com.demo.aidocumentassistant.common.error.PdfTooLargeException;
import com.demo.aidocumentassistant.common.error.ServerBusyException;
import com.demo.aidocumentassistant.document.DocumentPreview.ChunkStats;
import com.demo.aidocumentassistant.ingestion.ChunkingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer tests for {@link DocumentPreviewController}: upload handling, parameters and error mapping.
 */
@WebMvcTest(DocumentPreviewController.class)
@ActiveProfiles("local")
class DocumentPreviewControllerTest {

    private static final String PREVIEW_URL = "/api/v1/documents/preview";
    private static final byte[] PDF_BYTES = "%PDF-1.7 test".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentPreviewService documentPreviewService;

    @Test
    void returnsPreview() throws Exception {
        when(documentPreviewService.preview(anyString(), any(byte[].class), anyBoolean()))
                .thenReturn(samplePreview());

        mockMvc.perform(multipart(PREVIEW_URL).file(pdf("handbook.pdf")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.docId").value("preview-123"))
                .andExpect(jsonPath("$.fileName").value("handbook.pdf"))
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.chunkCount").value(1))
                .andExpect(jsonPath("$.chunkStats.avgChars").value(1500))
                .andExpect(jsonPath("$.chunks[0].pageNumber").value(1))
                .andExpect(jsonPath("$.chunksTruncated").value(false));
    }

    @Test
    void passesFileBytesAndDefaultsIncludeChunkTextToFalse() throws Exception {
        when(documentPreviewService.preview(anyString(), any(byte[].class), anyBoolean()))
                .thenReturn(samplePreview());

        mockMvc.perform(multipart(PREVIEW_URL).file(pdf("handbook.pdf")))
                .andExpect(status().isOk());

        verify(documentPreviewService).preview(eq("handbook.pdf"), aryEq(PDF_BYTES), eq(false));
    }

    @Test
    void passesIncludeChunkTextWhenRequested() throws Exception {
        when(documentPreviewService.preview(anyString(), any(byte[].class), anyBoolean()))
                .thenReturn(samplePreview());

        mockMvc.perform(multipart(PREVIEW_URL).file(pdf("handbook.pdf")).param("includeChunkText", "true"))
                .andExpect(status().isOk());

        verify(documentPreviewService).preview(eq("handbook.pdf"), aryEq(PDF_BYTES), eq(true));
    }

    @Test
    void keepsOnlyTheFileNameFromAFullClientPath() throws Exception {
        when(documentPreviewService.preview(anyString(), any(byte[].class), anyBoolean()))
                .thenReturn(samplePreview());

        mockMvc.perform(multipart(PREVIEW_URL).file(pdf("C:\\Users\\me\\handbook.pdf")))
                .andExpect(status().isOk());

        verify(documentPreviewService).preview(eq("handbook.pdf"), any(byte[].class), eq(false));
    }

    @Test
    void emptyFileIsBadRequest() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart(PREVIEW_URL).file(empty))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(documentPreviewService);
    }

    @Test
    void missingFilePartIsBadRequest() throws Exception {
        mockMvc.perform(multipart(PREVIEW_URL))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(documentPreviewService);
    }

    @Test
    void nonMultipartRequestIsUnsupported() throws Exception {
        mockMvc.perform(post(PREVIEW_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(documentPreviewService);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("pdfErrors")
    void mapsPdfErrorsToProblemResponses(RuntimeException error, int expectedStatus) throws Exception {
        when(documentPreviewService.preview(anyString(), any(byte[].class), anyBoolean())).thenThrow(error);

        mockMvc.perform(multipart(PREVIEW_URL).file(pdf("handbook.pdf")))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").exists());
    }

    static Stream<Arguments> pdfErrors() {
        return Stream.of(
                Arguments.of(new NotAPdfException("not a PDF"), 415),
                Arguments.of(new EncryptedPdfException("encrypted"), 422),
                Arguments.of(new NoExtractableTextException("no text"), 422),
                Arguments.of(new PdfTooLargeException("too large"), 422));
    }

    @Test
    void serverBusyIs503WithRetryAfter() throws Exception {
        when(documentPreviewService.preview(anyString(), any(byte[].class), anyBoolean()))
                .thenThrow(new ServerBusyException("Too many documents are being processed."));

        mockMvc.perform(multipart(PREVIEW_URL).file(pdf("handbook.pdf")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"));
    }

    // ---------------------------------------------------------------- helpers

    private static MockMultipartFile pdf(String fileName) {
        return new MockMultipartFile("file", fileName, MediaType.APPLICATION_PDF_VALUE, PDF_BYTES);
    }

    private static DocumentPreview samplePreview() {
        return new DocumentPreview(
                "preview-123",
                "handbook.pdf",
                PDF_BYTES.length,
                1,
                0,
                1,
                new ChunkStats(1500, 1500, 1500),
                new ChunkingProperties(1800, 270, 200),
                List.of(new DocumentPreview.PagePreview(1, 1500, 1, "Chapter 1 …")),
                List.of(new DocumentPreview.ChunkPreview(0, 1, 1500, "Chapter 1 …")),
                false,
                5);
    }
}
