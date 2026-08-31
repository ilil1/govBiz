package ai.govbiz.core.supportprogram.client.ai

fun interface AiSupportProgramRankingClient {
    fun rankSupportPrograms(
        request: AiSupportProgramRankingRequest,
    ): AiSupportProgramRankingPayload
}
