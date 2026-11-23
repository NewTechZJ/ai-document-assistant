/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * General API information shown in Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("AI Document Assistant API")
                .description("Upload PDF documents and ask questions answered from their content.")
                .version("v1"));
    }
}