package com.demo.aidocumentassistant.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkingPropertiesTest {

    @Test
    void defaultsAreTheDocumentedValues() {
        assertEquals(new ChunkingProperties(1800, 270, 200), ChunkingProperties.defaults());
    }

    @ParameterizedTest(name = "[{index}] target={0} overlap={1} min={2}")
    @CsvSource({
            "100,   0,  0",   // smallest target, no overlap, no merge
            "100,  50,  0",   // overlap = target / 2
            "100,   0, 99",   // min = target - 1
            "1800, 270, 200", // defaults
    })
    void acceptsValuesOnTheEdges(int target, int overlap, int min) {
        assertDoesNotThrow(() -> new ChunkingProperties(target, overlap, min));
    }

    @ParameterizedTest(name = "[{index}] target={0} overlap={1} min={2} -> {3}")
    @CsvSource({
            "99,    0,   0, target-chars",
            "100,  -1,   0, overlap-chars",
            "100,  51,   0, overlap-chars",  // overlap > target / 2
            "101,  51,   0, overlap-chars",  // integer division: 101 / 2 = 50
            "100,   0,  -1, min-chars",
            "100,   0, 100, min-chars",      // min = target
    })
    void rejectsValuesJustOutsideTheEdges(int target, int overlap, int min, String property) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ChunkingProperties(target, overlap, min));

        assertTrue(e.getMessage().contains("app.chunking." + property),
                "message names the property: " + e.getMessage());
    }
}
