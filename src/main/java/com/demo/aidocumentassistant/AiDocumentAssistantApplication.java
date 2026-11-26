/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the AI Document Assistant.
 */
@OpenAPIDefinition(info = @Info(
        title = "AI Document Assistant API",
        version = "v1",
        description = "Upload documents and ask questions. Answers are grounded in the document content and cite the pages used."))
@SpringBootApplication
public class AiDocumentAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDocumentAssistantApplication.class, args);
    }
}
