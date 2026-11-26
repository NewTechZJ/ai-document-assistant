/*
 * Copyright (c) 2026 NewTechZJ. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.demo.aidocumentassistant.vector;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the vector index.
 */
@Configuration
public class VectorConfig {

    /** In-memory index; contents are lost on restart. */
    @Bean
    VectorIndex vectorIndex() {
        return new InMemoryVectorIndex();
    }
}
