package ai.govbiz.core.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bizinfo")
public record BizInfoClientProperties(
        URI baseUrl,
        String serviceKey,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final Pattern PERCENT_ESCAPE = Pattern.compile("%[0-9a-fA-F]{2}");

    public BizInfoClientProperties {
        Objects.requireNonNull(baseUrl, "app.bizinfo.base-url must be configured");
        Objects.requireNonNull(connectTimeout, "app.bizinfo.connect-timeout must be configured");
        Objects.requireNonNull(readTimeout, "app.bizinfo.read-timeout must be configured");
        serviceKey = serviceKey == null ? "" : serviceKey.trim();

        validateBaseUrl(baseUrl);
        validatePositive(connectTimeout, "app.bizinfo.connect-timeout");
        validatePositive(readTimeout, "app.bizinfo.read-timeout");
    }

    /**
     * 공공데이터포털은 Encoding/Decoding 키를 모두 보여준다. URI template이 값을 정확히
     * 한 번 인코딩할 수 있도록 percent-encoded 키만 한 번 디코딩한다.
     */
    public String decodedServiceKey() {
        if (!PERCENT_ESCAPE.matcher(serviceKey).find()) {
            return serviceKey;
        }
        try {
            return URLDecoder.decode(serviceKey, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return serviceKey;
        }
    }

    private static void validateBaseUrl(URI baseUrl) {
        String scheme = baseUrl.getScheme();
        boolean supportedScheme = "http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme);
        if (!baseUrl.isAbsolute() || !supportedScheme || baseUrl.getHost() == null) {
            throw new IllegalArgumentException(
                    "app.bizinfo.base-url must be an absolute HTTP(S) URI with a host");
        }

        String path = baseUrl.getPath();
        if ((path != null && !path.isEmpty() && !"/".equals(path))
                || baseUrl.getQuery() != null
                || baseUrl.getFragment() != null
                || baseUrl.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "app.bizinfo.base-url must contain only scheme, host, and optional port");
        }
    }

    private static void validatePositive(Duration duration, String propertyName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be greater than zero");
        }
    }
}
