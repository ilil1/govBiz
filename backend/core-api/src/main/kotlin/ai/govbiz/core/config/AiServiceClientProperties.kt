package ai.govbiz.core.config

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
        validateBaseUrl(baseUrl)
        validatePositive(connectTimeout, "app.ai-service.connect-timeout")
        validatePositive(readTimeout, "app.ai-service.read-timeout")
    }

    companion object {
        private fun validateBaseUrl(baseUrl: URI) {
            val scheme = baseUrl.scheme
            val supportedScheme =
                "http".equals(scheme, ignoreCase = true) ||
                    "https".equals(scheme, ignoreCase = true)
            if (!baseUrl.isAbsolute || !supportedScheme || baseUrl.host == null) {
                throw IllegalArgumentException(
                    "app.ai-service.base-url must be an absolute HTTP(S) URI with a host",
                )
            }

            val port = baseUrl.port
            if (port != -1 && (port < 1 || port > 65_535)) {
                throw IllegalArgumentException(
                    "app.ai-service.base-url port must be between 1 and 65535",
                )
            }

            val path = baseUrl.path
            if (
                (path != null && path.isNotEmpty() && path != "/") ||
                baseUrl.query != null ||
                baseUrl.fragment != null ||
                baseUrl.userInfo != null
            ) {
                throw IllegalArgumentException(
                    "app.ai-service.base-url must contain only scheme, host, and optional port",
                )
            }
        }

        private fun validatePositive(duration: Duration, propertyName: String) {
            if (duration.isZero || duration.isNegative) {
                throw IllegalArgumentException("$propertyName must be greater than zero")
            }
        }
    }
}
