package io.basearchitecture.core.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-service")
public record AiServiceClientProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public AiServiceClientProperties {
        Objects.requireNonNull(baseUrl, "app.ai-service.base-url must be configured");
        Objects.requireNonNull(connectTimeout, "app.ai-service.connect-timeout must be configured");
        Objects.requireNonNull(readTimeout, "app.ai-service.read-timeout must be configured");

        validateBaseUrl(baseUrl);
        validatePositive(connectTimeout, "app.ai-service.connect-timeout");
        validatePositive(readTimeout, "app.ai-service.read-timeout");
    }

    private static void validateBaseUrl(URI baseUrl) {
        String scheme = baseUrl.getScheme();
        boolean supportedScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!baseUrl.isAbsolute() || !supportedScheme || baseUrl.getHost() == null) {
            throw new IllegalArgumentException(
                    "app.ai-service.base-url must be an absolute HTTP(S) URI with a host");
        }

        int port = baseUrl.getPort();
        if (port != -1 && (port < 1 || port > 65_535)) {
            throw new IllegalArgumentException(
                    "app.ai-service.base-url port must be between 1 and 65535");
        }

        String path = baseUrl.getPath();
        if ((path != null && !path.isEmpty() && !"/".equals(path))
                || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null
                || baseUrl.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "app.ai-service.base-url must contain only scheme, host, and optional port");
        }
    }

    private static void validatePositive(Duration duration, String propertyName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be greater than zero");
        }
    }
}
