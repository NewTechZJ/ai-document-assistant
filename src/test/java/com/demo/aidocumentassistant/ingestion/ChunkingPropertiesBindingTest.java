package com.demo.aidocumentassistant.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the app.chunking.* wiring through IngestionConfig, without starting the whole app. */
class ChunkingPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IngestionConfig.class);

    @Test
    void defaultsApplyWhenNothingIsSet() {
        contextRunner.run(context -> {
            assertEquals(ChunkingProperties.defaults(), context.getBean(ChunkingProperties.class));
            assertNotNull(context.getBean(Chunker.class));
            assertNotNull(context.getBean(PdfTextExtractor.class));
        });
    }

    @Test
    void propertiesOverrideTheDefaults() {
        contextRunner
                .withPropertyValues("app.chunking.target-chars=500", "app.chunking.overlap-chars=50",
                        "app.chunking.min-chars=40")
                .run(context -> assertEquals(new ChunkingProperties(500, 50, 40),
                        context.getBean(ChunkingProperties.class)));
    }

    @Test
    void invalidValueFailsStartupWithAClearMessage() {
        contextRunner
                .withPropertyValues("app.chunking.overlap-chars=5000")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure, "context should fail to start");
                    assertTrue(rootCause(failure).getMessage().contains("app.chunking.overlap-chars"),
                            "root cause: " + rootCause(failure).getMessage());
                });
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
