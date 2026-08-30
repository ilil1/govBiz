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

        val response = client.getHealth()

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
    fun sendsExactRankingRequestAndDecodesTheStructuredScores() {
        server.expect(requestTo(RANKING_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(
                content().json(
                    """
                    {
                      "originalQuery":"서울 AI 스타트업 지원사업",
                      "scoringVersion":"govbiz-support-program-ranking-v1",
                      "resultLimit":1,
                      "candidates":[{
                        "id":"program-1",
                        "title":"서울 AI 사업화 지원",
                        "organization":"서울경제진흥원",
                        "summary":"AI 창업기업 사업화 지원",
                        "categories":["AI","창업"],
                        "regions":["서울"],
                        "targetDescription":"서울 창업기업",
                        "applicationPeriod":"상시 접수",
                        "status":"OPEN"
                      }]
                    }
                    """.trimIndent(),
                ),
            )
            .andRespond(withSuccess(VALID_RANKING_RESPONSE, MediaType.APPLICATION_JSON))

        val response = client.rankSupportPrograms(rankingRequest())

        assertEquals("서울 AI 스타트업 지원사업", response.originalQuery)
        assertEquals(95, response.rankings?.single()?.totalScore)
        assertEquals("program-1", response.rankings?.single()?.programId)
    }

    @Test
    fun mapsRankingNoContentToInvalidResponse() {
        server.expect(requestTo(RANKING_URL)).andRespond(withNoContent())

        assertRankingFailure(AiServiceClientException.Failure.INVALID_RESPONSE)
    }

    @Test
    fun mapsMalformedRankingJsonToInvalidResponse() {
        server.expect(requestTo(RANKING_URL))
            .andRespond(withSuccess("{\"rankings\":", MediaType.APPLICATION_JSON))

        assertRankingFailure(AiServiceClientException.Failure.INVALID_RESPONSE)
    }

    @Test
    fun mapsRankingUnavailableStatusToUnavailable() {
        server.expect(requestTo(RANKING_URL))
            .andRespond(
                withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"detail\":\"unavailable\"}"),
            )

        assertRankingFailure(AiServiceClientException.Failure.UNAVAILABLE)
    }

    @Test
    fun mapsRankingGatewayTimeoutStatusToTimeout() {
        server.expect(requestTo(RANKING_URL))
            .andRespond(
                withStatus(HttpStatus.GATEWAY_TIMEOUT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"detail\":\"timeout\"}"),
            )

        assertRankingFailure(AiServiceClientException.Failure.TIMEOUT)
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

    private fun assertRankingFailure(expectedFailure: AiServiceClientException.Failure) {
        val exception = assertThrows(AiServiceClientException::class.java) {
            client.rankSupportPrograms(rankingRequest())
        }
        assertEquals(expectedFailure, exception.failure)
    }

    private fun rankingRequest() = AiSupportProgramRankingRequest(
        originalQuery = "서울 AI 스타트업 지원사업",
        scoringVersion = "govbiz-support-program-ranking-v1",
        resultLimit = 1,
        candidates = listOf(
            AiSupportProgramCandidateRequest(
                id = "program-1",
                title = "서울 AI 사업화 지원",
                organization = "서울경제진흥원",
                summary = "AI 창업기업 사업화 지원",
                categories = listOf("AI", "창업"),
                regions = listOf("서울"),
                targetDescription = "서울 창업기업",
                applicationPeriod = "상시 접수",
                status = "OPEN",
            ),
        ),
    )

    private companion object {
        const val BASE_URL = "http://ai-service.test:8000"
        const val HEALTH_URL = "$BASE_URL/internal/v1/health"
        const val RANKING_URL = "$BASE_URL/internal/v1/support-program-rankings/rank"
        val VALID_RESPONSE =
            """
            {"status":"up","service":"govbiz-ai-service"}
            """.trimIndent()
        val VALID_RANKING_RESPONSE =
            """
            {
              "originalQuery":"서울 AI 스타트업 지원사업",
              "scoringVersion":"govbiz-support-program-ranking-v1",
              "rankings":[{
                "programId":"program-1",
                "semanticRelevance":38,
                "targetFit":24,
                "regionFit":15,
                "applicationStatusFit":10,
                "supportTypeFit":8,
                "totalScore":95,
                "recommendationReasons":["서울 AI 창업기업 사업화 지원"]
              }]
            }
            """.trimIndent()
    }
}
