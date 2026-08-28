package ai.govbiz.core.client.ai

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.http.HttpConnectTimeoutException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class AiServiceClientTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var client: AiServiceClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
            .baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        server = MockRestServiceServer.bindTo(builder).build()
        client = AiServiceClient(builder.build())
    }

    @AfterEach
    fun verifiesEveryExpectedRequest() {
        server.verify()
    }

    @Test
    fun sendsExactHealthRequestAndDecodesValidJson() {
        server.expect(requestTo(HEALTH_URL))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
            .andRespond(withSuccess(VALID_RESPONSE, MediaType.APPLICATION_JSON))

        val response = requireNotNull(client.getHealth())

        assertEquals("up", response.status)
        assertEquals("govbiz-ai-service", response.service)
    }

    @Test
    fun mapsDownstream4xxToUpstreamError() {
        expectResponse(
            withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body("not-json"),
        )

        assertFailure(AiServiceClientException.Failure.UPSTREAM_ERROR)
    }

    @Test
    fun mapsDownstream5xxToUpstreamError() {
        expectResponse(
            withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.TEXT_PLAIN)
                .body("not-json"),
        )

        assertFailure(AiServiceClientException.Failure.UPSTREAM_ERROR)
    }

    @Test
    fun mapsUnexpectedSuccessfulStatusToUpstreamError() {
        expectResponse(
            withStatus(HttpStatus.CREATED)
                .contentType(MediaType.TEXT_PLAIN)
                .body("not-json"),
        )

        assertFailure(AiServiceClientException.Failure.UPSTREAM_ERROR)
    }

    @Test
    fun mapsRedirectStatusToUpstreamErrorWithoutDecodingBody() {
        expectResponse(
            withStatus(HttpStatus.FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body("not-json"),
        )

        assertFailure(AiServiceClientException.Failure.UPSTREAM_ERROR)
    }

    @Test
    fun mapsNoContentToInvalidResponse() {
        expectResponse(withNoContent())

        assertFailure(AiServiceClientException.Failure.INVALID_RESPONSE)
    }

    @Test
    fun mapsEmptySuccessBodyToInvalidResponse() {
        expectResponse(withSuccess("", MediaType.APPLICATION_JSON))

        assertFailure(AiServiceClientException.Failure.INVALID_RESPONSE)
    }

    @Test
    fun mapsWrongContentTypeToInvalidResponse() {
        expectResponse(withSuccess(VALID_RESPONSE, MediaType.TEXT_PLAIN))

        assertFailure(AiServiceClientException.Failure.INVALID_RESPONSE)
    }

    @Test
    fun mapsMalformedJsonToInvalidResponse() {
        expectResponse(withSuccess("{\"status\":", MediaType.APPLICATION_JSON))

        assertFailure(AiServiceClientException.Failure.INVALID_RESPONSE)
    }

    @Test
    fun mapsConnectionFailureToUnavailable() {
        expectResponse(withException(ConnectException("connection refused")))

        assertFailure(AiServiceClientException.Failure.UNAVAILABLE)
    }

    @Test
    fun mapsConnectTimeoutToTimeout() {
        expectResponse(withException(HttpConnectTimeoutException("connect timeout")))

        assertFailure(AiServiceClientException.Failure.TIMEOUT)
    }

    @Test
    fun mapsReadTimeoutToTimeout() {
        expectResponse(withException(SocketTimeoutException("read timeout")))

        assertFailure(AiServiceClientException.Failure.TIMEOUT)
    }

    @Test
    fun sendsExactSearchIntentRequestAndDecodesTheStructuredResponse() {
        server.expect(requestTo(SEARCH_INTENT_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(
                content().json(
                    """
                    {
                      "query":"서울 AI 스타트업 지원사업",
                      "acceptingOnly":true
                    }
                    """.trimIndent(),
                ),
            )
            .andRespond(withSuccess(VALID_INTENT_RESPONSE, MediaType.APPLICATION_JSON))

        val response = requireNotNull(
            client.analyzeSearchIntent(
                "서울 AI 스타트업 지원사업",
                true,
            ),
        )

        assertEquals("서울 AI 스타트업 지원사업", response.originalQuery)
        assertEquals(listOf("서울"), response.regions)
        assertEquals(listOf("AI", "창업"), response.categories)
    }

    @Test
    fun mapsSearchIntentNoContentToInvalidResponse() {
        server.expect(requestTo(SEARCH_INTENT_URL)).andRespond(withNoContent())

        assertIntentFailure(AiServiceClientException.Failure.INVALID_RESPONSE)
    }

    @Test
    fun mapsMalformedSearchIntentJsonToInvalidResponse() {
        server.expect(requestTo(SEARCH_INTENT_URL))
            .andRespond(withSuccess("{\"keywords\":", MediaType.APPLICATION_JSON))

        assertIntentFailure(AiServiceClientException.Failure.INVALID_RESPONSE)
    }

    @Test
    fun mapsSearchIntentUnavailableStatusToUnavailable() {
        server.expect(requestTo(SEARCH_INTENT_URL))
            .andRespond(
                withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"detail\":\"unavailable\"}"),
            )

        assertIntentFailure(AiServiceClientException.Failure.UNAVAILABLE)
    }

    @Test
    fun mapsSearchIntentGatewayTimeoutStatusToTimeout() {
        server.expect(requestTo(SEARCH_INTENT_URL))
            .andRespond(
                withStatus(HttpStatus.GATEWAY_TIMEOUT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"detail\":\"timeout\"}"),
            )

        assertIntentFailure(AiServiceClientException.Failure.TIMEOUT)
    }

    private fun expectResponse(response: ResponseCreator) {
        server.expect(requestTo(HEALTH_URL)).andRespond(response)
    }

    private fun assertFailure(expectedFailure: AiServiceClientException.Failure) {
        val exception = assertThrows(AiServiceClientException::class.java) {
            client.getHealth()
        }
        assertEquals(expectedFailure, exception.failure)
    }

    private fun assertIntentFailure(expectedFailure: AiServiceClientException.Failure) {
        val exception = assertThrows(AiServiceClientException::class.java) {
            client.analyzeSearchIntent("서울 AI", true)
        }
        assertEquals(expectedFailure, exception.failure)
    }

    private companion object {
        const val BASE_URL = "http://ai-service.test:8000"
        const val HEALTH_URL = "$BASE_URL/internal/v1/health"
        const val SEARCH_INTENT_URL = "$BASE_URL/internal/v1/search-intents/analyze"
        val VALID_RESPONSE =
            """
            {"status":"up","service":"govbiz-ai-service"}
            """.trimIndent()
        val VALID_INTENT_RESPONSE =
            """
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
            """.trimIndent()
    }
}
