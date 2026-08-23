package ai.govbiz.core.service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;

import ai.govbiz.core.client.ai.AiServiceClient;
import ai.govbiz.core.client.ai.AiServiceClientException;
import ai.govbiz.core.client.ai.AiServiceHealthPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceHealthServiceTest {

    @Mock
    private AiServiceClient client;

    @InjectMocks
    private AiServiceHealthService service;

    @Test
    void mapsValidatedPayloadToApplicationResult() {
        when(client.getHealth()).thenReturn(
                new AiServiceHealthPayload("up", "govbiz-ai-service"));

        AiServiceHealthResult result = service.getHealth();

        assertEquals("up", result.status());
        assertEquals("govbiz-ai-service", result.service());
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void rejectsMissingOrUnexpectedLiteralValues(AiServiceHealthPayload payload) {
        when(client.getHealth()).thenReturn(payload);

        AiServiceHealthException exception = assertThrows(
                AiServiceHealthException.class,
                service::getHealth);

        assertEquals(AiServiceHealthException.Failure.INVALID_RESPONSE, exception.failure());
    }

    @Test
    void rejectsNullPayloadAsInvalidResponse() {
        when(client.getHealth()).thenReturn(null);

        AiServiceHealthException exception = assertThrows(
                AiServiceHealthException.class,
                service::getHealth);

        assertEquals(AiServiceHealthException.Failure.INVALID_RESPONSE, exception.failure());
    }

    @ParameterizedTest
    @MethodSource("clientFailures")
    void preservesClientFailureCategory(
            AiServiceClientException clientException,
            AiServiceHealthException.Failure expectedFailure
    ) {
        when(client.getHealth()).thenThrow(clientException);

        AiServiceHealthException exception = assertThrows(
                AiServiceHealthException.class,
                service::getHealth);

        assertEquals(expectedFailure, exception.failure());
        assertSame(clientException, exception.getCause());
    }

    private static Stream<AiServiceHealthPayload> invalidPayloads() {
        return Stream.of(
                new AiServiceHealthPayload(null, "govbiz-ai-service"),
                new AiServiceHealthPayload("up", null),
                new AiServiceHealthPayload("down", "govbiz-ai-service"),
                new AiServiceHealthPayload("up", "another-service"));
    }

    private static Stream<Arguments> clientFailures() {
        return Stream.of(
                Arguments.of(
                        AiServiceClientException.upstreamError(
                                "unexpected status",
                                new IllegalStateException("test")),
                        AiServiceHealthException.Failure.UPSTREAM_ERROR),
                Arguments.of(
                        AiServiceClientException.invalidResponse(
                                "invalid response",
                                new IllegalArgumentException("test")),
                        AiServiceHealthException.Failure.INVALID_RESPONSE),
                Arguments.of(
                        AiServiceClientException.unavailable(new ConnectException("test")),
                        AiServiceHealthException.Failure.UNAVAILABLE),
                Arguments.of(
                        AiServiceClientException.timeout(new SocketTimeoutException("test")),
                        AiServiceHealthException.Failure.TIMEOUT));
    }
}
