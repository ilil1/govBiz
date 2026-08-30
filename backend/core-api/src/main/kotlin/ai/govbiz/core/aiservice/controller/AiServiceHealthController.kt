package ai.govbiz.core.aiservice.controller

import ai.govbiz.core.aiservice.dto.AiServiceHealthResponse
import ai.govbiz.core.aiservice.service.AiServiceHealthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/health/ai-service")
class AiServiceHealthController(
    private val aiServiceHealthService: AiServiceHealthService,
) {

    @GetMapping
    fun health(): AiServiceHealthResponse {
        val result = aiServiceHealthService.getHealth()
        return AiServiceHealthResponse(result.status, result.service)
    }
}
