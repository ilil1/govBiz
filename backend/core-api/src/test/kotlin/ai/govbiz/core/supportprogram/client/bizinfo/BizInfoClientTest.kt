package ai.govbiz.core.supportprogram.client.bizinfo

import java.net.URI
import java.time.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class BizInfoClientTest {

    private lateinit var server: MockRestServiceServer
    private lateinit var client: BizInfoClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        server = MockRestServiceServer.bindTo(builder).build()
        client = BizInfoClient(
            builder.build(),
            properties(ENCODED_KEY),
        )
    }

    @AfterEach
    fun verifiesEveryExpectedRequest() {
        server.verify()
    }

    @Test
    fun encodesAnAlreadyEncodedKeyExactlyOnceAndPaginatesFromTotalCount() {
        server.expect(requestTo(expectedUrl(1)))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(wrappedPage(1_001, "PBLN_1"), MediaType.APPLICATION_JSON))
        server.expect(requestTo(expectedUrl(2)))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(directItemsPage(1_001, "PBLN_2"), MediaType.APPLICATION_JSON))

        val programs = client.fetchAll()

        assertEquals(listOf("PBLN_1", "PBLN_2"), programs.map { it.id })
    }

    @Test
    fun rejectsAProtocolLevelFailureWithoutExposingItsMessage() {
        server.expect(requestTo(expectedUrl(1)))
            .andRespond(
                withSuccess(
                    """
                    {"response":{"header":{"resultCode":"30","resultMsg":"SECRET KEY ERROR"}}}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val exception = assertThrows(BizInfoClientException::class.java) {
            client.fetchAll()
        }

        assertEquals(BizInfoClientException.Failure.UPSTREAM_ERROR, exception.failure)
    }

    @Test
    fun blankKeyFailsBeforeMakingANetworkRequest() {
        client = BizInfoClient(RestClient.create(BASE_URL), properties("   "))

        val exception = assertThrows(BizInfoClientException::class.java) {
            client.fetchAll()
        }

        assertEquals(BizInfoClientException.Failure.NOT_CONFIGURED, exception.failure)
    }

    private fun properties(serviceKey: String) =
        BizInfoClientProperties(
            URI.create(BASE_URL),
            serviceKey,
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
        )

    private fun expectedUrl(pageNumber: Int) =
        BASE_URL + PATH +
            "?serviceKey=abc%2Bdef%3D" +
            "&pageNo=$pageNumber" +
            "&numOfRows=1000" +
            "&dataType=json"

    private fun wrappedPage(totalCount: Int, id: String) =
        """
        {
          "response": {
            "header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
            "body": {
              "items": {"item": [${item(id)}]},
              "numOfRows": 1000,
              "pageNo": 1,
              "totalCount": $totalCount
            }
          }
        }
        """.trimIndent()

    private fun directItemsPage(totalCount: Int, id: String) =
        """
        {
          "response": {
            "header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
            "body": {
              "items": [${item(id)}],
              "numOfRows": 1000,
              "pageNo": 2,
              "totalCount": $totalCount
            }
          }
        }
        """.trimIndent()

    private fun item(id: String) =
        """
        {
          "pblancNm": "서울 AI 사업",
          "pblancUrl": "https://www.bizinfo.go.kr/detail",
          "pblancId": "$id",
          "excInsttNm": "수행기관",
          "bsnsSumryCn": "<p>설명</p>",
          "pldirSportRealmLclasCodeNm": "기술",
          "reqstBeginEndDe": "2026-08-01 ~ 2026-09-01",
          "trgetNm": "중소기업",
          "hashtags": "기술,서울"
        }
        """.trimIndent()

    private companion object {
        const val BASE_URL = "https://public-data.test"
        const val PATH = "/1421000/bizinfo/pblancBsnsService"
        const val ENCODED_KEY = "abc%2Bdef%3D"
    }
}
