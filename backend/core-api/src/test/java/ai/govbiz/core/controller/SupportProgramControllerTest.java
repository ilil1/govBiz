package ai.govbiz.core.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import ai.govbiz.core.client.ai.AiServiceClientException;
import ai.govbiz.core.client.bizinfo.BizInfoClient;
import ai.govbiz.core.client.bizinfo.BizInfoClientException;
import ai.govbiz.core.client.bizinfo.BizInfoProgramPayload;
import ai.govbiz.core.service.SupportProgramSearchService;
import ai.govbiz.core.service.AnalyzedSearchIntent;
import ai.govbiz.core.service.AiSearchIntentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SupportProgramControllerTest {

    private static final String PATH = "/api/v1/support-programs/search";

    @Mock
    private BizInfoClient client;

    @Mock
    private AiSearchIntentService aiSearchIntentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-24T03:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        SupportProgramSearchService service = new SupportProgramSearchService(
                client,
                aiSearchIntentService,
                clock);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SupportProgramController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsTheStableFrontendContractIncludingNullableParsedDates() throws Exception {
        when(aiSearchIntentService.analyze("서울 AI", true)).thenReturn(emptyAnalyzedIntent());
        when(client.fetchAll()).thenReturn(List.of(payload("상시 접수")));

        mockMvc.perform(get(PATH).queryParam("query", "  서울 AI  "))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query").value("서울 AI"))
                .andExpect(jsonPath("$.programs[0].id").value("PBLN_TEST"))
                .andExpect(jsonPath("$.programs[0].status").value("OPEN"))
                .andExpect(jsonPath("$.programs[0].applicationPeriod").value("상시 접수"))
                .andExpect(jsonPath("$.programs[0].applicationStartDate").value((Object) null))
                .andExpect(jsonPath("$.programs[0].applicationEndDate").value((Object) null))
                .andExpect(jsonPath("$.programs[0].sourceName").value("기업마당"))
                .andExpect(jsonPath("$.programs[0].sourceUrl")
                        .value("https://www.bizinfo.go.kr/detail?id=PBLN_TEST"));
    }

    @Test
    void hidesConfigurationAndUpstreamDetailsBehindAStableProblem() throws Exception {
        when(aiSearchIntentService.analyze("서울", true)).thenReturn(emptyAnalyzedIntent());
        when(client.fetchAll()).thenThrow(BizInfoClientException.notConfigured());

        mockMvc.perform(get(PATH).queryParam("query", "서울"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SUPPORT_PROGRAM_SOURCE_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.instance").value(PATH))
                .andExpect(content().string(not(containsString("service key"))));
    }

    @Test
    void returnsUnavailableWhenRequiredAiIntentAnalysisIsUnavailable() throws Exception {
        when(aiSearchIntentService.analyze("서울", true)).thenThrow(
                AiServiceClientException.unavailable(new RuntimeException("private endpoint")));

        mockMvc.perform(get(PATH).queryParam("query", "서울"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.instance").value(PATH))
                .andExpect(content().string(not(containsString("private endpoint"))));
    }

    @Test
    void requiresASearchQueryParameter() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isBadRequest());
    }

    private static AnalyzedSearchIntent emptyAnalyzedIntent() {
        return new AnalyzedSearchIntent(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                null);
    }

    private static BizInfoProgramPayload payload(String period) {
        return new BizInfoProgramPayload(
                "서울 AI 지원사업",
                "https://www.bizinfo.go.kr/detail?id=PBLN_TEST",
                "PBLN_TEST",
                "중소벤처기업부",
                "수행기관",
                "<p>AI &amp; 기술 지원</p>",
                "AI",
                "2026-08-20 10:00:00",
                period,
                "2026-08-21 10:00:00",
                "중소기업",
                "AI,서울",
                "온라인",
                null);
    }
}
