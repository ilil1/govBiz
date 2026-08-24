package ai.govbiz.core.service;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import ai.govbiz.core.client.ai.AiSearchIntentAnalysisMode;
import ai.govbiz.core.client.ai.AiSearchIntentPayload;
import ai.govbiz.core.client.ai.AiServiceClient;
import ai.govbiz.core.client.ai.AiServiceClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSearchIntentServiceTest {

    private static final String QUERY = "서울 AI 스타트업 지원사업";

    @Mock
    private AiServiceClient client;

    private AiSearchIntentService service;

    @BeforeEach
    void setUp() {
        service = new AiSearchIntentService(client);
    }

    @Test
    void returnsATrimmedDefensiveCopyOfAValidIntent() {
        when(client.analyzeSearchIntent(QUERY, true)).thenReturn(new AiSearchIntentPayload(
                QUERY,
                List.of(" 스타트업 ", "스타트업"),
                List.of("서울"),
                List.of("AI", "창업"),
                List.of("창업기업"),
                true,
                true,
                " 업력을 알려주세요. ",
                AiSearchIntentAnalysisMode.LLM));

        Optional<AnalyzedSearchIntent> result = service.analyze("  " + QUERY + "  ", true);

        assertTrue(result.isPresent());
        assertEquals(List.of("스타트업"), result.orElseThrow().keywords());
        assertEquals(List.of("서울"), result.orElseThrow().regions());
        assertEquals("업력을 알려주세요.", result.orElseThrow().clarificationQuestion());
        verify(client).analyzeSearchIntent(QUERY, true);
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void rejectsResponsesThatViolateTheInternalContract(AiSearchIntentPayload payload) {
        when(client.analyzeSearchIntent(QUERY, true)).thenReturn(payload);

        assertTrue(service.analyze(QUERY, true).isEmpty());
    }

    @Test
    void fallsBackWhenTheAiServiceCannotBeUsed() {
        when(client.analyzeSearchIntent(QUERY, false)).thenThrow(
                AiServiceClientException.timeout(new SocketTimeoutException("test")));

        assertTrue(service.analyze(QUERY, false).isEmpty());
    }

    @Test
    void skipsTheAiServiceForBlankQueries() {
        assertTrue(service.analyze("   ", true).isEmpty());
        verifyNoInteractions(client);
    }

    private static Stream<AiSearchIntentPayload> invalidPayloads() {
        AiSearchIntentPayload valid = validPayload();
        return Stream.of(
                null,
                new AiSearchIntentPayload(
                        "다른 질문",
                        valid.keywords(), valid.regions(), valid.categories(), valid.targetTerms(),
                        true, false, null, valid.analysisMode()),
                new AiSearchIntentPayload(
                        QUERY,
                        valid.keywords(), valid.regions(), valid.categories(), valid.targetTerms(),
                        false, false, null, valid.analysisMode()),
                new AiSearchIntentPayload(
                        QUERY,
                        null, valid.regions(), valid.categories(), valid.targetTerms(),
                        true, false, null, valid.analysisMode()),
                new AiSearchIntentPayload(
                        QUERY,
                        valid.keywords(), List.of("달나라"), valid.categories(), valid.targetTerms(),
                        true, false, null, valid.analysisMode()),
                new AiSearchIntentPayload(
                        QUERY,
                        valid.keywords(), valid.regions(), List.of("법률"), valid.targetTerms(),
                        true, false, null, valid.analysisMode()),
                new AiSearchIntentPayload(
                        QUERY,
                        List.of("AI\n지시"), valid.regions(), valid.categories(), valid.targetTerms(),
                        true, false, null, valid.analysisMode()),
                new AiSearchIntentPayload(
                        QUERY,
                        valid.keywords(), valid.regions(), valid.categories(), valid.targetTerms(),
                        true, false, "불필요한 질문", valid.analysisMode()),
                new AiSearchIntentPayload(
                        QUERY,
                        valid.keywords(), valid.regions(), valid.categories(), valid.targetTerms(),
                        true, true, null, valid.analysisMode()),
                new AiSearchIntentPayload(
                        QUERY,
                        valid.keywords(), valid.regions(), valid.categories(), valid.targetTerms(),
                        true, false, null, null));
    }

    private static AiSearchIntentPayload validPayload() {
        return new AiSearchIntentPayload(
                QUERY,
                List.of("스타트업"),
                List.of("서울"),
                List.of("AI", "창업"),
                List.of("창업기업"),
                true,
                false,
                null,
                AiSearchIntentAnalysisMode.LLM);
    }
}
