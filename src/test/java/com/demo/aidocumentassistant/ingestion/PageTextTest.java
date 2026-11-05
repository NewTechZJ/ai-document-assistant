package com.demo.aidocumentassistant.ingestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTextTest {

    @Test
    void pageNumbersAreOneBased() {
        assertThrows(IllegalArgumentException.class, () -> new PageText(0, "text"));
        assertThrows(IllegalArgumentException.class, () -> new PageText(-1, "text"));
    }

    @Test
    void textMustNotBeNull() {
        assertThrows(NullPointerException.class, () -> new PageText(1, null));
    }

    @Test
    void isBlankIsTrueForEmptyAndWhitespaceText() {
        assertTrue(new PageText(1, "").isBlank());
        assertTrue(new PageText(1, " \n\t ").isBlank());
        assertFalse(new PageText(1, "x").isBlank());
    }
}
