package io.basearchitecture.core.config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import io.basearchitecture.core.client.ai.AiServiceClient;
import io.basearchitecture.core.client.ai.AiServiceClientException;
import io.basearchitecture.core.client.ai.AiServiceHealthPayload;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServiceClientConfigIntegrationTest {

    private static final String HEALTH_PATH = "/internal/v1/health";
    private static final String VALID_RESPONSE = """
            {"status":"up","service":"base-architecture-ai-service"}
            """;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);

    private HttpServer server;
    private ExecutorService serverExecutor;

    @BeforeEach
    void setUpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "ai-service-config-test-server");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(serverExecutor);
    }

    @AfterEach
    void stopServerAndExecutor() throws InterruptedException {
        server.stop(0);
        serverExecutor.shutdownNow();
        assertTrue(
                serverExecutor.awaitTermination(2, TimeUnit.SECONDS),
                "local HTTP test server threads must terminate");
    }

    @Test
    void usesHttp11WithoutAttemptingH2cUpgrade() {
        AtomicReference<String> protocol = new AtomicReference<>();
        AtomicReference<String> upgradeHeader = new AtomicReference<>();
        AtomicReference<String> http2SettingsHeader = new AtomicReference<>();
        AtomicReference<String> acceptHeader = new AtomicReference<>();

        server.createContext(HEALTH_PATH, exchange -> {
            protocol.set(exchange.getProtocol());
            upgradeHeader.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            http2SettingsHeader.set(exchange.getRequestHeaders().getFirst("HTTP2-Settings"));
            acceptHeader.set(exchange.getRequestHeaders().getFirst(HttpHeaders.ACCEPT));
            sendJson(exchange, VALID_RESPONSE);
        });
        server.start();

        AiServiceHealthPayload response = createClient(Duration.ofSeconds(1)).getHealth();

        assertAll(
                () -> assertEquals("up", response.status()),
                () -> assertEquals("base-architecture-ai-service", response.service()),
                () -> assertEquals("HTTP/1.1", protocol.get()),
                () -> assertNull(upgradeHeader.get()),
                () -> assertNull(http2SettingsHeader.get()),
                () -> assertEquals(MediaType.APPLICATION_JSON_VALUE, acceptHeader.get()));
    }

    @Test
    void doesNotFollowRedirects() {
        AtomicInteger healthRequestCount = new AtomicInteger();
        AtomicInteger redirectTargetRequestCount = new AtomicInteger();

        server.createContext(HEALTH_PATH, exchange -> {
            healthRequestCount.incrementAndGet();
            exchange.getResponseHeaders().set(HttpHeaders.LOCATION, "/redirect-target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirect-target", exchange -> {
            redirectTargetRequestCount.incrementAndGet();
            sendJson(exchange, VALID_RESPONSE);
        });
        server.start();

        AiServiceClientException exception = assertThrows(
                AiServiceClientException.class,
                () -> createClient(Duration.ofSeconds(1)).getHealth());

        assertAll(
                () -> assertEquals(
                        AiServiceClientException.Failure.UPSTREAM_ERROR,
                        exception.failure()),
                () -> assertEquals(1, healthRequestCount.get()),
                () -> assertEquals(0, redirectTargetRequestCount.get()));
    }

    @Test
    void appliesConfiguredReadTimeoutToARealRequest() {
        CountDownLatch requestReceived = new CountDownLatch(1);

        server.createContext(HEALTH_PATH, exchange -> {
            requestReceived.countDown();
            try {
                Thread.sleep(Duration.ofSeconds(2).toMillis());
                sendJson(exchange, VALID_RESPONSE);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        server.start();

        AiServiceClientException exception = assertTimeout(
                Duration.ofSeconds(3),
                () -> assertThrows(
                        AiServiceClientException.class,
                        () -> createClient(Duration.ofMillis(150)).getHealth()));

        assertAll(
                () -> assertEquals(
                        AiServiceClientException.Failure.TIMEOUT,
                        exception.failure()),
                () -> assertEquals(0, requestReceived.getCount()));
    }

    private AiServiceClient createClient(Duration readTimeout) {
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        AiServiceClientProperties properties = new AiServiceClientProperties(
                baseUrl,
                CONNECT_TIMEOUT,
                readTimeout);
        RestClient restClient = new AiServiceClientConfig().aiServiceRestClient(
                RestClient.builder(),
                properties);
        return new AiServiceClient(restClient);
    }

    private static void sendJson(HttpExchange exchange, String body) throws IOException {
        byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                HttpHeaders.CONTENT_TYPE,
                MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(200, responseBody.length);
        try (var output = exchange.getResponseBody()) {
            output.write(responseBody);
        } finally {
            exchange.close();
        }
    }
}
