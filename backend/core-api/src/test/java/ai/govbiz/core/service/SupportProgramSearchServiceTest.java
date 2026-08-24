package ai.govbiz.core.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import ai.govbiz.core.client.bizinfo.BizInfoClient;
import ai.govbiz.core.client.bizinfo.BizInfoProgramPayload;
import ai.govbiz.core.client.ai.AiSearchIntentAnalysisMode;
import ai.govbiz.core.domain.support.SupportProgram;
import ai.govbiz.core.domain.support.SupportProgramStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportProgramSearchServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-24T03:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Mock
    private BizInfoClient client;

    @Mock
    private AiSearchIntentService aiSearchIntentService;

    private SupportProgramSearchService service;

    @BeforeEach
    void setUp() {
        service = new SupportProgramSearchService(client, aiSearchIntentService, CLOCK);
    }

    @Test
    void derivesDatesAndHonestStatusesWhilePreservingTheOriginalPeriod() {
        when(client.fetchAll()).thenReturn(List.of(
                payload("open", "<p>AI &amp; 기술<br>지원</p>",
                        "2026-08-20 ~ 2026-09-11", "AI,서울"),
                payload("rolling", "상시 사업", "2026-08-01 ~ 예산 소진시까지", "경영,서울"),
                payload("upcoming", "예정 사업", "추후 공지", "창업,서울"),
                payload("unknown", "상이 사업", "세부사업별 상이", "기술,서울"),
                payload("closed", "종료 사업", "2026-07-01 ~ 2026-07-31", "기술,서울")));

        Map<String, SupportProgram> byId = service.search("", false).programs().stream()
                .collect(Collectors.toMap(SupportProgram::id, Function.identity()));

        assertEquals(SupportProgramStatus.OPEN, byId.get("open").status());
        assertEquals("AI & 기술 지원", byId.get("open").summary());
        assertFalse(byId.get("open").summary().contains("<"));
        assertEquals("2026-08-20", byId.get("open").applicationStartDate().toString());
        assertEquals("2026-09-11", byId.get("open").applicationEndDate().toString());

        assertEquals(SupportProgramStatus.OPEN, byId.get("rolling").status());
        assertEquals("2026-08-01 ~ 예산 소진시까지", byId.get("rolling").applicationPeriod());
        assertNull(byId.get("rolling").applicationEndDate());
        assertEquals(SupportProgramStatus.UPCOMING, byId.get("upcoming").status());
        assertEquals(SupportProgramStatus.UNKNOWN, byId.get("unknown").status());
        assertEquals(SupportProgramStatus.CLOSED, byId.get("closed").status());

        List<SupportProgram> accepting = service.search("", true).programs();
        assertEquals(List.of("open", "rolling"),
                accepting.stream().map(SupportProgram::id).toList());
        verify(client, times(1)).fetchAll();
    }

    @Test
    void tokenizesNaturalLanguageAndUnderstandsRegionPostpositions() {
        when(aiSearchIntentService.analyze("서울에서 AI 지원사업 찾아줘", true))
                .thenReturn(Optional.empty());
        when(client.fetchAll()).thenReturn(List.of(
                payload("seoul-ai", "AI 기술 사업", "상시 접수", "AI,서울"),
                payload("gyeonggi", "유통 사업", "상시 접수", "내수,경기")));

        SupportProgramSearchResult result = service.search("서울에서 AI 지원사업 찾아줘", true);

        assertEquals(List.of("seoul-ai"),
                result.programs().stream().map(SupportProgram::id).toList());
        assertTrue(result.programs().getFirst().matchedReasons().contains("서울 지역"));
        assertTrue(result.programs().getFirst().matchedReasons().contains("AI 분야"));
    }

    @Test
    void keepsAMissingApplicationPeriodVisibleWithoutGuessingItsStatus() {
        when(client.fetchAll()).thenReturn(List.of(
                payload("missing-period", "기간 미제공 사업", null, "경영,서울")));

        SupportProgram program = service.search("", false).programs().getFirst();

        assertEquals("정보 없음", program.applicationPeriod());
        assertNull(program.applicationStartDate());
        assertNull(program.applicationEndDate());
        assertEquals(SupportProgramStatus.UNKNOWN, program.status());
    }

    @Test
    void mergesAGroundedAiCategoryAliasThatTheLocalParserCannotCanonicalize() {
        String query = "스타트업 프로그램";
        when(aiSearchIntentService.analyze(query, true)).thenReturn(Optional.of(
                new AnalyzedSearchIntent(
                        List.of("스타트업"),
                        List.of(),
                        List.of("창업"),
                        List.of(),
                        false,
                        null,
                        AiSearchIntentAnalysisMode.LLM)));
        when(client.fetchAll()).thenReturn(List.of(
                payload("startup", "초기 기업 육성", "상시 접수", "창업,부산"),
                payload("export", "해외 판로 개척", "상시 접수", "수출,부산")));

        SupportProgramSearchResult result = service.search(query, true);

        assertEquals(List.of("startup"),
                result.programs().stream().map(SupportProgram::id).toList());
        assertTrue(result.programs().getFirst().matchedReasons().contains("창업 분야"));
    }

    @Test
    void ignoresValidButUngroundedAiTerms() {
        String query = "서울 AI";
        when(aiSearchIntentService.analyze(query, true)).thenReturn(Optional.of(
                new AnalyzedSearchIntent(
                        List.of("반도체"),
                        List.of("부산"),
                        List.of("수출"),
                        List.of("창업기업"),
                        false,
                        null,
                        AiSearchIntentAnalysisMode.LLM)));
        when(client.fetchAll()).thenReturn(List.of(
                payload("grounded", "인공지능 기술", "상시 접수", "AI,서울"),
                payload(
                        "hallucinated",
                        "반도체 전용",
                        "상시 접수",
                        "수출,부산",
                        "창업기업")));

        SupportProgramSearchResult result = service.search(query, true);

        assertEquals(List.of("grounded"),
                result.programs().stream().map(SupportProgram::id).toList());
    }

    @Test
    void doesNotGroundShortAsciiCategoriesInsideLongerEnglishWords() {
        String query = "training 지원";
        when(aiSearchIntentService.analyze(query, true)).thenReturn(Optional.of(
                new AnalyzedSearchIntent(
                        List.of(),
                        List.of(),
                        List.of("AI"),
                        List.of(),
                        false,
                        null,
                        AiSearchIntentAnalysisMode.LLM)));
        when(client.fetchAll()).thenReturn(List.of(
                payload("training", "직무 교육", "상시 접수", "인력,서울"),
                payload("other", "인공지능 기술", "상시 접수", "AI,부산")));

        SupportProgramSearchResult result = service.search(query, true);

        assertEquals(List.of("training"),
                result.programs().stream().map(SupportProgram::id).toList());
    }

    @Test
    void capsRegionScoreAndMatchedReasonsAtOnePerProgram() {
        String query = "서울 부산 대구 인천 광주 대전 울산 세종 수출";
        when(aiSearchIntentService.analyze(query, true)).thenReturn(Optional.empty());
        when(client.fetchAll()).thenReturn(List.of(
                payload("nationwide", "일반 경영 사업", "상시 접수", "경영,전국"),
                payload("seoul-export", "해외 판로 사업", "상시 접수", "수출,서울")));

        SupportProgramSearchResult result = service.search(query, true);

        assertEquals("seoul-export", result.programs().getFirst().id());
        SupportProgram nationwide = result.programs().stream()
                .filter(program -> program.id().equals("nationwide"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, nationwide.matchedReasons().stream()
                .filter(reason -> reason.endsWith(" 지역"))
                .count());
    }

    @Test
    void skipsAiAnalysisForABlankQuery() {
        when(client.fetchAll()).thenReturn(List.of(
                payload("open", "AI 기술 사업", "상시 접수", "AI,서울")));

        service.search("   ", true);

        verifyNoInteractions(aiSearchIntentService);
    }

    private static BizInfoProgramPayload payload(
            String id,
            String summary,
            String period,
            String hashtags
    ) {
        return payload(id, summary, period, hashtags, "중소기업");
    }

    private static BizInfoProgramPayload payload(
            String id,
            String summary,
            String period,
            String hashtags,
            String target
    ) {
        return new BizInfoProgramPayload(
                id + " 공고",
                "https://www.bizinfo.go.kr/detail?id=" + id,
                id,
                "중소벤처기업부",
                "수행기관",
                summary,
                hashtags.split(",")[0],
                "2026-08-20 10:00:00",
                period,
                "2026-08-21 10:00:00",
                target,
                hashtags,
                "온라인 신청",
                null);
    }
}
