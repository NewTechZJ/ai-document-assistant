package com.demo.aidocumentassistant.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class)
public class HealthControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void healthTest() throws Exception {
        mockMvc.perform(get("/actuator/health").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
