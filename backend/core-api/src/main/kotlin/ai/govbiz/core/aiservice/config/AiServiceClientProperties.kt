package ai.govbiz.core.aiservice.config

import ai.govbiz.core._common.config.validateHttpBaseUrl
import ai.govbiz.core._common.config.validatePositiveDuration
import java.net.URI
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.ai-service")
data class AiServiceClientProperties(
    val baseUrl: URI,
    val connectTimeout: Duration,
    val readTimeout: Duration,
) {

    init {
        validateHttpBaseUrl(baseUrl, "app.ai-service.base-url")
        validatePositiveDuration(connectTimeout, "app.ai-service.connect-timeout")
        validatePositiveDuration(readTimeout, "app.ai-service.read-timeout")
    }
}
