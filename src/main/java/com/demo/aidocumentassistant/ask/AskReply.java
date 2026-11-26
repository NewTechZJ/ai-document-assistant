/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.ask;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response of {@code POST /api/ask}.
 */
@Schema(description = "Answer to a question about a document, with the pages it was based on")
public record AskReply(

        @Schema(description = "Answer generated from the document content",
                example = "The refund window is 30 days from delivery [page 4].")
        String answer,

        @Schema(description = "One entry per page the answer cites, in order of first citation; empty when the answer was not found")
        List<Citation> citations,

        @Schema(description = "Input tokens used; 0 if no chunk passed the score threshold and the model was not called; null if the provider did not report it",
                example = "2480")
        Integer inputTokens,

        @Schema(description = "Output tokens used; 0 if the model was not called; null if the provider did not report it",
                example = "64")
        Integer outputTokens
) {

    /**
     * A page cited in the answer.
     */
    @Schema(description = "A page cited in the answer")
    public record Citation(

            @Schema(description = "Page number in the document, starting at 1", example = "4")
            int pageNumber,

            @Schema(description = "Start of the best-matching text chunk on that page",
                    example = "Refunds are available within 30 days of delivery, provided the item is unused...")
            String snippet,

            @Schema(description = "Cosine similarity between the chunk and the question, rounded to 3 decimals",
                    example = "0.742")
            double score
    ) {
    }
}
