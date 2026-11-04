package com.demo.aidocumentassistant.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/actuator/health")
    public HealthResponse healthCheck() {
        return new HealthResponse("AI Document Assistant", "UP");
    }
}

