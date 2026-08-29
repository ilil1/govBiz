package ai.govbiz.core.service

import ai.govbiz.core.client.ai.AiSearchIntentPayload
import ai.govbiz.core.client.ai.AiServiceClient
import ai.govbiz.core.client.ai.AiServiceClientException
import java.net.SocketTimeoutException
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AiSearchIntentServiceTest {

    @Mock
    private lateinit var client: AiServiceClient

    private lateinit var service: AiSearchIntentService

    @BeforeEach
    fun setUp() {
        service = AiSearchIntentService(client)
    }

    @Test
    fun returnsATrimmedDefensiveCopyOfAValidIntent() {
        Mockito.doReturn(
            AiSearchIntentPayload(
                QUERY,
                listOf(" 스타트업 ", "스타트업"),
                listOf("서울"),
                listOf("AI", "창업"),
                listOf("창업기업"),
                true,
                true,
                " 업력을 알려주세요. ",
            ),
        )
            .`when`(client)
            .analyzeSearchIntent(QUERY, true)

        val result = service.analyze("  $QUERY  ", true)

        assertEquals(listOf("스타트업"), result.keywords)
        assertEquals(listOf("서울"), result.regions)
        assertEquals("업력을 알려주세요.", result.clarificationQuestion)
        Mockito.verify(client).analyzeSearchIntent(QUERY, true)
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    fun rejectsResponsesThatViolateTheInternalContract(payload: AiSearchIntentPayload) {
        Mockito.doReturn(payload).`when`(client).analyzeSearchIntent(QUERY, true)

        val exception = assertThrows(AiServiceClientException::class.java) {
            service.analyze(QUERY, true)
        }

        assertEquals(AiServiceClientException.Failure.INVALID_RESPONSE, exception.failure)
    }

    @Test
    fun propagatesFailureWhenTheRequiredAiServiceCannotBeUsed() {
        Mockito.doThrow(
            AiServiceClientException.timeout(SocketTimeoutException("test")),
        )
            .`when`(client)
            .analyzeSearchIntent(QUERY, false)

        val exception = assertThrows(AiServiceClientException::class.java) {
            service.analyze(QUERY, false)
        }

        assertEquals(AiServiceClientException.Failure.TIMEOUT, exception.failure)
    }

    @Test
    fun rejectsBlankQueriesBeforeCallingTheAiService() {
        assertThrows(IllegalArgumentException::class.java) {
            service.analyze("   ", true)
        }
        Mockito.verifyNoInteractions(client)
    }

    private companion object {
        const val QUERY = "서울 AI 스타트업 지원사업"

        @JvmStatic
        fun invalidPayloads(): Stream<AiSearchIntentPayload> {
            val valid = validPayload()
            return Stream.of(
                AiSearchIntentPayload(
                    "다른 질문",
                    valid.keywords,
                    valid.regions,
                    valid.categories,
                    valid.targetTerms,
                    true,
                    false,
                    null,
                ),
                AiSearchIntentPayload(
                    QUERY,
                    valid.keywords,
                    valid.regions,
                    valid.categories,
                    valid.targetTerms,
                    false,
                    false,
                    null,
                ),
                AiSearchIntentPayload(
                    QUERY,
                    null,
                    valid.regions,
                    valid.categories,
                    valid.targetTerms,
                    true,
                    false,
                    null,
                ),
                AiSearchIntentPayload(
                    QUERY,
                    valid.keywords,
                    listOf("달나라"),
                    valid.categories,
                    valid.targetTerms,
                    true,
                    false,
                    null,
                ),
                AiSearchIntentPayload(
                    QUERY,
                    valid.keywords,
                    valid.regions,
                    listOf("법률"),
                    valid.targetTerms,
                    true,
                    false,
                    null,
                ),
                AiSearchIntentPayload(
                    QUERY,
                    listOf("AI\n지시"),
                    valid.regions,
                    valid.categories,
                    valid.targetTerms,
                    true,
                    false,
                    null,
                ),
                AiSearchIntentPayload(
                    QUERY,
                    valid.keywords,
                    valid.regions,
                    valid.categories,
                    valid.targetTerms,
                    true,
                    false,
                    "불필요한 질문",
                ),
                AiSearchIntentPayload(
                    QUERY,
                    valid.keywords,
                    valid.regions,
                    valid.categories,
                    valid.targetTerms,
                    true,
                    true,
                    null,
                ),
            )
        }

        private fun validPayload() =
            AiSearchIntentPayload(
                QUERY,
                listOf("스타트업"),
                listOf("서울"),
                listOf("AI", "창업"),
                listOf("창업기업"),
                true,
                false,
                null,
            )
    }
}
