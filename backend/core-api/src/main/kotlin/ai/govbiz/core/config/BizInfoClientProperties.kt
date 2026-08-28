package ai.govbiz.core.config

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.regex.Pattern
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.bizinfo")
class BizInfoClientProperties(
    baseUrl: URI?,
    serviceKey: String?,
    connectTimeout: Duration?,
    readTimeout: Duration?,
) {

    val baseUrl: URI = baseUrl
        ?: throw NullPointerException("app.bizinfo.base-url must be configured")
    val serviceKey: String = serviceKey?.trimLikeJava().orEmpty()
    val connectTimeout: Duration = connectTimeout
        ?: throw NullPointerException("app.bizinfo.connect-timeout must be configured")
    val readTimeout: Duration = readTimeout
        ?: throw NullPointerException("app.bizinfo.read-timeout must be configured")

    init {
        validateBaseUrl(this.baseUrl)
        validatePositive(this.connectTimeout, "app.bizinfo.connect-timeout")
        validatePositive(this.readTimeout, "app.bizinfo.read-timeout")
    }

    /**
     * 공공데이터포털은 Encoding/Decoding 키를 모두 보여준다. URI template이 값을 정확히
     * 한 번 인코딩할 수 있도록 percent-encoded 키만 한 번 디코딩한다.
     */
    fun decodedServiceKey(): String {
        if (!PERCENT_ESCAPE.matcher(serviceKey).find()) {
            return serviceKey
        }
        return try {
            URLDecoder.decode(serviceKey, StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            serviceKey
        }
    }

    companion object {
        private val PERCENT_ESCAPE: Pattern = Pattern.compile("%[0-9a-fA-F]{2}")

        private fun validateBaseUrl(baseUrl: URI) {
            val scheme = baseUrl.scheme
            val supportedScheme =
                "http".equals(scheme, ignoreCase = true) ||
                    "https".equals(scheme, ignoreCase = true)
            if (!baseUrl.isAbsolute || !supportedScheme || baseUrl.host == null) {
                throw IllegalArgumentException(
                    "app.bizinfo.base-url must be an absolute HTTP(S) URI with a host",
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
                    "app.bizinfo.base-url must contain only scheme, host, and optional port",
                )
            }
        }

        private fun validatePositive(duration: Duration, propertyName: String) {
            if (duration.isZero || duration.isNegative) {
                throw IllegalArgumentException("$propertyName must be greater than zero")
            }
        }

        private fun String.trimLikeJava(): String = trim { it <= ' ' }
    }
}
