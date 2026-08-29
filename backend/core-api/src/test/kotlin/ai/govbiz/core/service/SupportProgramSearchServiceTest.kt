package ai.govbiz.core.service

import ai.govbiz.core.client.bizinfo.BizInfoClient
import ai.govbiz.core.client.bizinfo.BizInfoProgramPayload
import ai.govbiz.core.domain.support.SupportProgram
import ai.govbiz.core.domain.support.SupportProgramStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class SupportProgramSearchServiceTest {

    @Mock
    private lateinit var client: BizInfoClient

    @Mock
    private lateinit var aiSearchIntentService: AiSearchIntentService

    private lateinit var service: SupportProgramSearchService

    @BeforeEach
    fun setUp() {
        service = SupportProgramSearchService(client, aiSearchIntentService, CLOCK)
    }

    @Test
    fun derivesDatesAndHonestStatusesWhilePreservingTheOriginalPeriod() {
        Mockito.doReturn(
            listOf(
                payload(
                    "open",
                    "<p>AI &amp; 기술<br>지원</p>",
                    "2026-08-20 ~ 2026-09-11",
                    "AI,서울",
                ),
                payload("rolling", "상시 사업", "2026-08-01 ~ 예산 소진시까지", "경영,서울"),
                payload("upcoming", "예정 사업", "추후 공지", "창업,서울"),
                payload("unknown", "상이 사업", "세부사업별 상이", "기술,서울"),
                payload("closed", "종료 사업", "2026-07-01 ~ 2026-07-31", "기술,서울"),
            ),
        )
            .`when`(client)
            .fetchAll()

        val byId = service.search("", false).programs.associateBy { it.id }

        assertEquals(SupportProgramStatus.OPEN, byId.getValue("open").status)
        assertEquals("AI & 기술 지원", byId.getValue("open").summary)
        assertFalse(byId.getValue("open").summary.contains("<"))
        assertEquals("2026-08-20", byId.getValue("open").applicationStartDate.toString())
        assertEquals("2026-09-11", byId.getValue("open").applicationEndDate.toString())

        assertEquals(SupportProgramStatus.OPEN, byId.getValue("rolling").status)
        assertEquals("2026-08-01 ~ 예산 소진시까지", byId.getValue("rolling").applicationPeriod)
        assertNull(byId.getValue("rolling").applicationEndDate)
        assertEquals(SupportProgramStatus.UPCOMING, byId.getValue("upcoming").status)
        assertEquals(SupportProgramStatus.UNKNOWN, byId.getValue("unknown").status)
        assertEquals(SupportProgramStatus.CLOSED, byId.getValue("closed").status)

        val accepting = service.search("", true).programs
        assertEquals(listOf("open", "rolling"), accepting.map { it.id })
        Mockito.verify(client, Mockito.times(1)).fetchAll()
    }

    @Test
    fun tokenizesNaturalLanguageAndUnderstandsRegionPostpositions() {
        Mockito.doReturn(emptyAnalyzedIntent())
            .`when`(aiSearchIntentService)
            .analyze("서울에서 AI 지원사업 찾아줘", true)
        Mockito.doReturn(
            listOf(
                payload("seoul-ai", "AI 기술 사업", "상시 접수", "AI,서울"),
                payload("gyeonggi", "유통 사업", "상시 접수", "내수,경기"),
            ),
        )
            .`when`(client)
            .fetchAll()

        val result = service.search("서울에서 AI 지원사업 찾아줘", true)

        assertEquals(listOf("seoul-ai"), result.programs.map { it.id })
        assertTrue(result.programs.first().matchedReasons.contains("서울 지역"))
        assertTrue(result.programs.first().matchedReasons.contains("AI 분야"))
    }

    @Test
    fun returnsRuntimeUnmodifiableListsAcrossPublicAndCachedBoundaries() {
        val query = "서울 AI"
        Mockito.doReturn(emptyAnalyzedIntent())
            .`when`(aiSearchIntentService)
            .analyze(query, true)
        Mockito.doReturn(
            listOf(
                payload("immutable", "AI 기술 사업", "상시 접수", "서울,부산")
                    .copy(category = "AI/기술"),
            ),
        )
            .`when`(client)
            .fetchAll()

        val result = service.search(query, true)
        val program = result.programs.single()

        assertThrows(UnsupportedOperationException::class.java) {
            (result.programs as MutableList<SupportProgram>).add(program)
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (program.categories as MutableList<String>).add("수출")
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (program.regions as MutableList<String>).add("경기")
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (program.matchedReasons as MutableList<String>).add("변조된 사유")
        }
    }

    @Test
    fun keepsAMissingApplicationPeriodVisibleWithoutGuessingItsStatus() {
        Mockito.doReturn(
            listOf(payload("missing-period", "기간 미제공 사업", null, "경영,서울")),
        )
            .`when`(client)
            .fetchAll()

        val program = service.search("", false).programs.first()

        assertEquals("정보 없음", program.applicationPeriod)
        assertNull(program.applicationStartDate)
        assertNull(program.applicationEndDate)
        assertEquals(SupportProgramStatus.UNKNOWN, program.status)
    }

    @Test
    fun mergesAGroundedAiCategoryAliasThatTheLocalParserCannotCanonicalize() {
        val query = "스타트업 프로그램"
        Mockito.doReturn(
            AnalyzedSearchIntent(
                listOf("스타트업"),
                emptyList(),
                listOf("창업"),
                emptyList(),
                false,
                null,
            ),
        )
            .`when`(aiSearchIntentService)
            .analyze(query, true)
        Mockito.doReturn(
            listOf(
                payload("startup", "초기 기업 육성", "상시 접수", "창업,부산"),
                payload("export", "해외 판로 개척", "상시 접수", "수출,부산"),
            ),
        )
            .`when`(client)
            .fetchAll()

        val result = service.search(query, true)

        assertEquals(listOf("startup"), result.programs.map { it.id })
        assertTrue(result.programs.first().matchedReasons.contains("창업 분야"))
    }

    @Test
    fun ignoresValidButUngroundedAiTerms() {
        val query = "서울 AI"
        Mockito.doReturn(
            AnalyzedSearchIntent(
                listOf("반도체"),
                listOf("부산"),
                listOf("수출"),
                listOf("창업기업"),
                false,
                null,
            ),
        )
            .`when`(aiSearchIntentService)
            .analyze(query, true)
        Mockito.doReturn(
            listOf(
                payload("grounded", "인공지능 기술", "상시 접수", "AI,서울"),
                payload(
                    "hallucinated",
                    "반도체 전용",
                    "상시 접수",
                    "수출,부산",
                    "창업기업",
                ),
            ),
        )
            .`when`(client)
            .fetchAll()

        val result = service.search(query, true)

        assertEquals(listOf("grounded"), result.programs.map { it.id })
    }

    @Test
    fun doesNotGroundShortAsciiCategoriesInsideLongerEnglishWords() {
        val query = "training 지원"
        Mockito.doReturn(
            AnalyzedSearchIntent(
                emptyList(),
                emptyList(),
                listOf("AI"),
                emptyList(),
                false,
                null,
            ),
        )
            .`when`(aiSearchIntentService)
            .analyze(query, true)
        Mockito.doReturn(
            listOf(
                payload("training", "직무 교육", "상시 접수", "인력,서울"),
                payload("other", "인공지능 기술", "상시 접수", "AI,부산"),
            ),
        )
            .`when`(client)
            .fetchAll()

        val result = service.search(query, true)

        assertEquals(listOf("training"), result.programs.map { it.id })
    }

    @Test
    fun capsRegionScoreAndMatchedReasonsAtOnePerProgram() {
        val query = "서울 부산 대구 인천 광주 대전 울산 세종 수출"
        Mockito.doReturn(emptyAnalyzedIntent())
            .`when`(aiSearchIntentService)
            .analyze(query, true)
        Mockito.doReturn(
            listOf(
                payload("nationwide", "일반 경영 사업", "상시 접수", "경영,전국"),
                payload("seoul-export", "해외 판로 사업", "상시 접수", "수출,서울"),
            ),
        )
            .`when`(client)
            .fetchAll()

        val result = service.search(query, true)

        assertEquals("seoul-export", result.programs.first().id)
        val nationwide = result.programs.first { it.id == "nationwide" }
        assertEquals(1, nationwide.matchedReasons.count { it.endsWith(" 지역") })
    }

    @Test
    fun skipsAiAnalysisForABlankQuery() {
        Mockito.doReturn(
            listOf(payload("open", "AI 기술 사업", "상시 접수", "AI,서울")),
        )
            .`when`(client)
            .fetchAll()

        service.search("   ", true)

        Mockito.verifyNoInteractions(aiSearchIntentService)
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

    private fun payload(
        id: String,
        summary: String,
        period: String?,
        hashtags: String,
        target: String = "중소기업",
    ) =
        BizInfoProgramPayload(
            "$id 공고",
            "https://www.bizinfo.go.kr/detail?id=$id",
            id,
            "중소벤처기업부",
            "수행기관",
            summary,
            hashtags.substringBefore(','),
            "2026-08-20 10:00:00",
            period,
            "2026-08-21 10:00:00",
            target,
            hashtags,
            "온라인 신청",
        )

    private companion object {
        val CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-08-24T03:00:00Z"),
            ZoneId.of("Asia/Seoul"),
        )
    }
}
