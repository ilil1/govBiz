package ai.govbiz.core.client.bizinfo;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import ai.govbiz.core.config.BizInfoClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BizInfoClientTest {

    private static final String BASE_URL = "https://public-data.test";
    private static final String PATH = "/1421000/bizinfo/pblancBsnsService";
    private static final String ENCODED_KEY = "abc%2Bdef%3D";

    private MockRestServiceServer server;
    private BizInfoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BizInfoClient(
                builder.build(),
                properties(ENCODED_KEY));
    }

    @AfterEach
    void verifiesEveryExpectedRequest() {
        server.verify();
    }

    @Test
    void encodesAnAlreadyEncodedKeyExactlyOnceAndPaginatesFromTotalCount() {
        server.expect(requestTo(expectedUrl(1)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(wrappedPage(1_001, "PBLN_1"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(expectedUrl(2)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(directItemsPage(1_001, "PBLN_2"), MediaType.APPLICATION_JSON));

        List<BizInfoProgramPayload> programs = client.fetchAll();

        assertEquals(List.of("PBLN_1", "PBLN_2"),
                programs.stream().map(BizInfoProgramPayload::id).toList());
    }

    @Test
    void rejectsAProtocolLevelFailureWithoutExposingItsMessage() {
        server.expect(requestTo(expectedUrl(1)))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"30","resultMsg":"SECRET KEY ERROR"}}}
                        """, MediaType.APPLICATION_JSON));

        BizInfoClientException exception = assertThrows(
                BizInfoClientException.class,
                client::fetchAll);

        assertEquals(BizInfoClientException.Failure.UPSTREAM_ERROR, exception.failure());
    }

    @Test
    void blankKeyFailsBeforeMakingANetworkRequest() {
        client = new BizInfoClient(RestClient.create(BASE_URL), properties("   "));

        BizInfoClientException exception = assertThrows(
                BizInfoClientException.class,
                client::fetchAll);

        assertEquals(BizInfoClientException.Failure.NOT_CONFIGURED, exception.failure());
    }

    private static BizInfoClientProperties properties(String serviceKey) {
        return new BizInfoClientProperties(
                URI.create(BASE_URL),
                serviceKey,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }

    private static String expectedUrl(int pageNumber) {
        return BASE_URL + PATH
                + "?serviceKey=abc%2Bdef%3D"
                + "&pageNo=" + pageNumber
                + "&numOfRows=1000"
                + "&dataType=json";
    }

    private static String wrappedPage(int totalCount, String id) {
        return """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
                    "body": {
                      "items": {"item": [%s]},
                      "numOfRows": 1000,
                      "pageNo": 1,
                      "totalCount": %d
                    }
                  }
                }
                """.formatted(item(id), totalCount);
    }

    private static String directItemsPage(int totalCount, String id) {
        return """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
                    "body": {
                      "items": [%s],
                      "numOfRows": 1000,
                      "pageNo": 2,
                      "totalCount": %d
                    }
                  }
                }
                """.formatted(item(id), totalCount);
    }

    private static String item(String id) {
        return """
                {
                  "pblancNm": "서울 AI 사업",
                  "pblancUrl": "https://www.bizinfo.go.kr/detail",
                  "pblancId": "%s",
                  "excInsttNm": "수행기관",
                  "bsnsSumryCn": "<p>설명</p>",
                  "pldirSportRealmLclasCodeNm": "기술",
                  "reqstBeginEndDe": "2026-08-01 ~ 2026-09-01",
                  "trgetNm": "중소기업",
                  "hashtags": "기술,서울"
                }
                """.formatted(id);
    }
}
