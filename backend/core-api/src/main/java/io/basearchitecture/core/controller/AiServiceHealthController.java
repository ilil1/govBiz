package io.basearchitecture.core.controller;

import io.basearchitecture.core.dto.AiServiceHealthResponse;
import io.basearchitecture.core.service.AiServiceHealthResult;
import io.basearchitecture.core.service.AiServiceHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health/ai-service")
public class AiServiceHealthController {

    private final AiServiceHealthService aiServiceHealthService;

    public AiServiceHealthController(AiServiceHealthService aiServiceHealthService) {
        this.aiServiceHealthService = aiServiceHealthService;
    }

    @GetMapping
    public AiServiceHealthResponse health() {
        AiServiceHealthResult result = aiServiceHealthService.getHealth();
        return new AiServiceHealthResponse(result.status(), result.service());
    }
}
