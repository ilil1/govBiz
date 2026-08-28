package ai.govbiz.core.controller

import ai.govbiz.core.dto.HealthResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/health")
class HealthController(
    @Value("\${spring.application.name}") private val serviceName: String,
) {

    @GetMapping
    fun health(): HealthResponse = HealthResponse.up(serviceName)
}
