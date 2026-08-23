package io.basearchitecture.core.config;

import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServiceClientPropertiesTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8000",
            "https://ai-service.internal/",
            "http://ai-service.internal:1",
            "http://ai-service.internal:65535"
    })
    void acceptsHttpUrisAndPositiveDurations(String baseUrl) {
        AiServiceClientProperties properties = new AiServiceClientProperties(
                URI.create(baseUrl),
                CONNECT_TIMEOUT,
                READ_TIMEOUT);

        assertEquals(URI.create(baseUrl), properties.baseUrl());
        assertEquals(CONNECT_TIMEOUT, properties.connectTimeout());
        assertEquals(READ_TIMEOUT, properties.readTimeout());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://ai-service.internal:8000",
            "http:///internal/v1/health",
            "http://ai-service.internal:8000/base",
            "http://user:password@ai-service.internal:8000",
            "http://ai-service.internal:8000?debug=true",
            "http://ai-service.internal:8000#health",
            "http://ai-service.internal:0",
            "http://ai-service.internal:65536"
    })
    void rejectsUnsupportedOrUnsafeBaseUris(String baseUrl) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiServiceClientProperties(
                        URI.create(baseUrl),
                        CONNECT_TIMEOUT,
                        READ_TIMEOUT));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://ai-service.internal:0",
            "http://ai-service.internal:65536"
    })
    void rejectsOutOfRangePortsWhenApplicationContextStarts(String baseUrl) {
        new ApplicationContextRunner()
                .withUserConfiguration(AiServiceClientConfig.class)
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withPropertyValues(
                        "app.ai-service.base-url=" + baseUrl,
                        "app.ai-service.connect-timeout=1s",
                        "app.ai-service.read-timeout=2s")
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    assertNotNull(startupFailure);

                    Throwable rootCause = rootCause(startupFailure);
                    assertInstanceOf(IllegalArgumentException.class, rootCause);
                    assertTrue(rootCause.getMessage().contains("port must be between 1 and 65535"));
                });
    }

    @ParameterizedTest
    @MethodSource("nonPositiveTimeouts")
    void rejectsZeroOrNegativeTimeouts(Duration connectTimeout, Duration readTimeout) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AiServiceClientProperties(
                        URI.create("http://127.0.0.1:8000"),
                        connectTimeout,
                        readTimeout));
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThrows(
                NullPointerException.class,
                () -> new AiServiceClientProperties(null, CONNECT_TIMEOUT, READ_TIMEOUT));
        assertThrows(
                NullPointerException.class,
                () -> new AiServiceClientProperties(
                        URI.create("http://127.0.0.1:8000"),
                        null,
                        READ_TIMEOUT));
        assertThrows(
                NullPointerException.class,
                () -> new AiServiceClientProperties(
                        URI.create("http://127.0.0.1:8000"),
                        CONNECT_TIMEOUT,
                        null));
    }

    private static Stream<Arguments> nonPositiveTimeouts() {
        return Stream.of(
                Arguments.of(Duration.ZERO, READ_TIMEOUT),
                Arguments.of(Duration.ofMillis(-1), READ_TIMEOUT),
                Arguments.of(CONNECT_TIMEOUT, Duration.ZERO),
                Arguments.of(CONNECT_TIMEOUT, Duration.ofMillis(-1)));
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
