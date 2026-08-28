package ai.govbiz.core.controller

import ai.govbiz.core.client.ai.AiServiceClientException
import ai.govbiz.core.client.bizinfo.BizInfoClient
import ai.govbiz.core.client.bizinfo.BizInfoClientException
import ai.govbiz.core.client.bizinfo.BizInfoProgramPayload
import ai.govbiz.core.service.AiSearchIntentService
import ai.govbiz.core.service.AnalyzedSearchIntent
import ai.govbiz.core.service.SupportProgramSearchService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class SupportProgramControllerTest {

    @Mock
    private lateinit var client: BizInfoClient

    @Mock
    private lateinit var aiSearchIntentService: AiSearchIntentService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val clock = Clock.fixed(
            Instant.parse("2026-08-24T03:00:00Z"),
            ZoneId.of("Asia/Seoul"),
        )
        val service = SupportProgramSearchService(
            client,
            aiSearchIntentService,
            clock,
        )
        mockMvc = MockMvcBuilders
            .standaloneSetup(SupportProgramController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun returnsTheStableFrontendContractIncludingNullableParsedDates() {
        Mockito.doReturn(emptyAnalyzedIntent())
            .`when`(aiSearchIntentService)
            .analyze("서울 AI", true)
        Mockito.doReturn(listOf(payload("상시 접수")))
            .`when`(client)
            .fetchAll()

        mockMvc.perform(get(PATH).queryParam("query", "  서울 AI  "))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.query").value("서울 AI"))
            .andExpect(jsonPath("$.programs[0].id").value("PBLN_TEST"))
            .andExpect(jsonPath("$.programs[0].status").value("OPEN"))
            .andExpect(jsonPath("$.programs[0].applicationPeriod").value("상시 접수"))
            .andExpect(jsonPath("$.programs[0].applicationStartDate").value(nullValue()))
            .andExpect(jsonPath("$.programs[0].applicationEndDate").value(nullValue()))
            .andExpect(jsonPath("$.programs[0].sourceName").value("기업마당"))
            .andExpect(
                jsonPath("$.programs[0].sourceUrl")
                    .value("https://www.bizinfo.go.kr/detail?id=PBLN_TEST"),
            )
    }

    @Test
    fun hidesConfigurationAndUpstreamDetailsBehindAStableProblem() {
        Mockito.doReturn(emptyAnalyzedIntent())
            .`when`(aiSearchIntentService)
            .analyze("서울", true)
        Mockito.doThrow(BizInfoClientException.notConfigured()).`when`(client).fetchAll()

        mockMvc.perform(get(PATH).queryParam("query", "서울"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("SUPPORT_PROGRAM_SOURCE_NOT_CONFIGURED"))
            .andExpect(jsonPath("$.instance").value(PATH))
            .andExpect(content().string(not(containsString("service key"))))
    }

    @Test
    fun returnsUnavailableWhenRequiredAiIntentAnalysisIsUnavailable() {
        Mockito.doThrow(
            AiServiceClientException.unavailable(RuntimeException("private endpoint")),
        )
            .`when`(aiSearchIntentService)
            .analyze("서울", true)

        mockMvc.perform(get(PATH).queryParam("query", "서울"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"))
            .andExpect(jsonPath("$.instance").value(PATH))
            .andExpect(content().string(not(containsString("private endpoint"))))
    }

    @Test
    fun requiresASearchQueryParameter() {
        mockMvc.perform(get(PATH))
            .andExpect(status().isBadRequest())
    }

    private fun emptyAnalyzedIntent() =
        AnalyzedSearchIntent(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            false,
            null,
        )

    private fun payload(period: String) =
        BizInfoProgramPayload(
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
            null,
        )

    private companion object {
        const val PATH = "/api/v1/support-programs/search"
    }
}
