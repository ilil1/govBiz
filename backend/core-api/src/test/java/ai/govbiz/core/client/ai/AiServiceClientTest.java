package ai.govbiz.core.client.ai;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiServiceClientTest {

    private static final String BASE_URL = "http://ai-service.test:8000";
    private static final String HEALTH_URL = BASE_URL + "/internal/v1/health";
    private static final String SEARCH_INTENT_URL =
            BASE_URL + "/internal/v1/search-intents/analyze";
    private static final String VALID_RESPONSE = """
            {"status":"up","service":"govbiz-ai-service"}
            """;
    private static final String VALID_INTENT_RESPONSE = """
            {
              "originalQuery":"서울 AI 스타트업 지원사업",
              "keywords":["스타트업"],
              "regions":["서울"],
              "categories":["AI","창업"],
              "targetTerms":["창업기업"],
              "acceptingOnly":true,
              "clarificationNeeded":false,
              "clarificationQuestion":null
            }
            """;

    private MockRestServiceServer server;
    private AiServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiServiceClient(builder.build());
    }

    @AfterEach
    void verifiesEveryExpectedRequest() {
        server.verify();
    }

    @Test
    void sendsExactHealthRequestAndDecodesValidJson() {
        server.expect(requestTo(HEALTH_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(VALID_RESPONSE, MediaType.APPLICATION_JSON));

        AiServiceHealthPayload response = client.getHealth();

        assertEquals("up", response.status());
        assertEquals("govbiz-ai-service", response.service());
    }

    @Test
    void mapsDownstream4xxToUpstreamError() {
        expectResponse(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body("not-json"));

        assertFailure(AiServiceClientException.Failure.UPSTREAM_ERROR);
    }

    @Test
    void mapsDownstream5xxToUpstreamError() {
        expectResponse(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.TEXT_PLAIN)
                .body("not-json"));

        assertFailure(AiServiceClientException.Failure.UPSTREAM_ERROR);
    }

    @Test
    void mapsUnexpectedSuccessfulStatusToUpstreamError() {
        expectResponse(withStatus(HttpStatus.CREATED)
                .contentType(MediaType.TEXT_PLAIN)
                .body("not-json"));

        assertFailure(AiServiceClientException.Failure.UPSTREAM_ERROR);
    }

    @Test
    void mapsRedirectStatusToUpstreamErrorWithoutDecodingBody() {
        expectResponse(withStatus(HttpStatus.FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body("not-json"));

        assertFailure(AiServiceClientException.Failure.UPSTREAM_ERROR);
    }

    @Test
    void mapsNoContentToInvalidResponse() {
        expectResponse(withNoContent());

        assertFailure(AiServiceClientException.Failure.INVALID_RESPONSE);
    }

    @Test
    void mapsEmptySuccessBodyToInvalidResponse() {
        expectResponse(withSuccess("", MediaType.APPLICATION_JSON));

        assertFailure(AiServiceClientException.Failure.INVALID_RESPONSE);
    }

    @Test
    void mapsWrongContentTypeToInvalidResponse() {
        expectResponse(withSuccess(VALID_RESPONSE, MediaType.TEXT_PLAIN));

        assertFailure(AiServiceClientException.Failure.INVALID_RESPONSE);
    }

    @Test
    void mapsMalformedJsonToInvalidResponse() {
        expectResponse(withSuccess("{\"status\":", MediaType.APPLICATION_JSON));

        assertFailure(AiServiceClientException.Failure.INVALID_RESPONSE);
    }

    @Test
    void mapsConnectionFailureToUnavailable() {
        expectResponse(withException(new ConnectException("connection refused")));

        assertFailure(AiServiceClientException.Failure.UNAVAILABLE);
    }

    @Test
    void mapsConnectTimeoutToTimeout() {
        expectResponse(withException(new HttpConnectTimeoutException("connect timeout")));

        assertFailure(AiServiceClientException.Failure.TIMEOUT);
    }

    @Test
    void mapsReadTimeoutToTimeout() {
        expectResponse(withException(new SocketTimeoutException("read timeout")));

        assertFailure(AiServiceClientException.Failure.TIMEOUT);
    }

    @Test
    void sendsExactSearchIntentRequestAndDecodesTheStructuredResponse() {
        server.expect(requestTo(SEARCH_INTENT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .json("""
                                {
                                  "query":"서울 AI 스타트업 지원사업",
                                  "acceptingOnly":true
                                }
                                """))
                .andRespond(withSuccess(VALID_INTENT_RESPONSE, MediaType.APPLICATION_JSON));

        AiSearchIntentPayload response = client.analyzeSearchIntent(
                "서울 AI 스타트업 지원사업",
                true);

        assertEquals("서울 AI 스타트업 지원사업", response.originalQuery());
        assertEquals(List.of("서울"), response.regions());
        assertEquals(List.of("AI", "창업"), response.categories());
    }

    @Test
    void mapsSearchIntentNoContentToInvalidResponse() {
        server.expect(requestTo(SEARCH_INTENT_URL)).andRespond(withNoContent());

        assertIntentFailure(AiServiceClientException.Failure.INVALID_RESPONSE);
    }

    @Test
    void mapsMalformedSearchIntentJsonToInvalidResponse() {
        server.expect(requestTo(SEARCH_INTENT_URL))
                .andRespond(withSuccess("{\"keywords\":", MediaType.APPLICATION_JSON));

        assertIntentFailure(AiServiceClientException.Failure.INVALID_RESPONSE);
    }

    @Test
    void mapsSearchIntentUnavailableStatusToUnavailable() {
        server.expect(requestTo(SEARCH_INTENT_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"unavailable\"}"));

        assertIntentFailure(AiServiceClientException.Failure.UNAVAILABLE);
    }

    @Test
    void mapsSearchIntentGatewayTimeoutStatusToTimeout() {
        server.expect(requestTo(SEARCH_INTENT_URL))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"timeout\"}"));

        assertIntentFailure(AiServiceClientException.Failure.TIMEOUT);
    }

    private void expectResponse(org.springframework.test.web.client.ResponseCreator response) {
        server.expect(requestTo(HEALTH_URL)).andRespond(response);
    }

    private void assertFailure(AiServiceClientException.Failure expectedFailure) {
        AiServiceClientException exception = assertThrows(
                AiServiceClientException.class,
                client::getHealth);
        assertEquals(expectedFailure, exception.failure());
    }

    private void assertIntentFailure(AiServiceClientException.Failure expectedFailure) {
        AiServiceClientException exception = assertThrows(
                AiServiceClientException.class,
                () -> client.analyzeSearchIntent("서울 AI", true));
        assertEquals(expectedFailure, exception.failure());
    }
}
