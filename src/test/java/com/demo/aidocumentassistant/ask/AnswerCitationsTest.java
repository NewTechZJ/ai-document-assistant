package com.demo.aidocumentassistant.ask;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerCitationsTest {

    @Test
    void findsPagesInOrderOfFirstAppearanceWithoutDuplicates() {
        String answer = "Refunds take 30 days [page 4]. Shipping is free [page 2]. Again [page 4].";
        assertEquals(List.of(4, 2), List.copyOf(AnswerCitations.citedPages(answer)));
    }

    @Test
    void acceptsCommonVariantsTheModelMightUse() {
        String answer = "A [Page 3]. B [pages 5, 7]. C [p. 9]. D [ page 11 ]. E [pages 12 and 13].";
        assertEquals(List.of(3, 5, 7, 9, 11, 12, 13), List.copyOf(AnswerCitations.citedPages(answer)));
    }

    @Test
    void ignoresTextThatIsNotACitation() {
        String answer = "See page 4 and [note 2] and [12].";
        assertTrue(AnswerCitations.citedPages(answer).isEmpty());
    }

    @Test
    void nullAnswerHasNoCitations() {
        assertTrue(AnswerCitations.citedPages(null).isEmpty());
    }

    @Test
    void recognisesTheNotFoundSentence() {
        assertTrue(AnswerCitations.isNotFound(PromptBuilder.NOT_FOUND_ANSWER));
        assertTrue(AnswerCitations.isNotFound("  i couldn’t find that in the document.\n"));
        assertFalse(AnswerCitations.isNotFound("The refund window is 30 days [page 4]."));
        assertFalse(AnswerCitations.isNotFound(null));
    }
}
