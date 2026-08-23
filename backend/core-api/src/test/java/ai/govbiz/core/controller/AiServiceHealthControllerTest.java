package ai.govbiz.core.controller;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;

import ai.govbiz.core.client.ai.AiServiceClient;
import ai.govbiz.core.client.ai.AiServiceClientException;
import ai.govbiz.core.client.ai.AiServiceHealthPayload;
import ai.govbiz.core.service.AiServiceHealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiServiceHealthControllerTest {

    private static final String PATH = "/api/v1/health/ai-service";

    @Mock
    private AiServiceClient client;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AiServiceHealthService service = new AiServiceHealthService(client);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiServiceHealthController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsPublicHealthContract() throws Exception {
        when(client.getHealth()).thenReturn(
                new AiServiceHealthPayload("up", "govbiz-ai-service"));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("up"))
                .andExpect(jsonPath("$.service").value("govbiz-ai-service"));
    }

    @ParameterizedTest
    @MethodSource("problemCases")
    void mapsFailuresToStableProblemDetails(
            AiServiceClientException clientException,
            int expectedStatus,
            String expectedCode,
            String expectedType,
            String expectedTitle,
            String expectedDetail
    ) throws Exception {
        when(client.getHealth()).thenThrow(clientException);

        mockMvc.perform(get(PATH))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.instance").value(PATH))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.type").value(expectedType))
                .andExpect(jsonPath("$.title").value(expectedTitle))
                .andExpect(jsonPath("$.detail").value(expectedDetail))
                .andExpect(content().string(not(containsString("AI Service returned HTTP 503"))))
                .andExpect(content().string(not(containsString("invalid JSON"))))
                .andExpect(content().string(not(containsString("do not expose this"))))
                .andExpect(content().string(not(containsString("internal timeout"))))
                .andExpect(content().string(not(containsString("127.0.0.1"))))
                .andExpect(content().string(not(containsString("8000"))));
    }

    private static Stream<Arguments> problemCases() {
        return Stream.of(
                Arguments.of(
                        AiServiceClientException.upstreamError(
                                "AI Service returned HTTP 503",
                                new IllegalStateException("do not expose this")),
                        502,
                        "AI_SERVICE_UPSTREAM_ERROR",
                        "urn:govbiz:problem:ai-service-upstream-error",
                        "AI Service Upstream Error",
                        "AI Service returned an unexpected HTTP status."),
                Arguments.of(
                        AiServiceClientException.invalidResponse(
                                "invalid JSON",
                                new IllegalArgumentException("do not expose this")),
                        502,
                        "AI_SERVICE_INVALID_RESPONSE",
                        "urn:govbiz:problem:ai-service-invalid-response",
                        "AI Service Invalid Response",
                        "AI Service returned an invalid response."),
                Arguments.of(
                        AiServiceClientException.unavailable(
                                new ConnectException("127.0.0.1:8000")),
                        503,
                        "AI_SERVICE_UNAVAILABLE",
                        "urn:govbiz:problem:ai-service-unavailable",
                        "AI Service Unavailable",
                        "AI Service is currently unavailable."),
                Arguments.of(
                        AiServiceClientException.timeout(
                                new SocketTimeoutException("internal timeout")),
                        504,
                        "AI_SERVICE_TIMEOUT",
                        "urn:govbiz:problem:ai-service-timeout",
                        "AI Service Gateway Timeout",
                        "AI Service did not respond within the configured timeout."));
    }
}
