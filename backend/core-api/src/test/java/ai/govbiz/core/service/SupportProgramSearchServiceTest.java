package ai.govbiz.core.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import ai.govbiz.core.client.bizinfo.BizInfoClient;
import ai.govbiz.core.client.bizinfo.BizInfoProgramPayload;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportProgramSearchServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-24T03:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Mock
    private BizInfoClient client;

    private SupportProgramSearchService service;

    @BeforeEach
    void setUp() {
        service = new SupportProgramSearchService(client, CLOCK);
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

    private static BizInfoProgramPayload payload(
            String id,
            String summary,
            String period,
            String hashtags
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
                "중소기업",
                hashtags,
                "온라인 신청",
                null);
    }
}
