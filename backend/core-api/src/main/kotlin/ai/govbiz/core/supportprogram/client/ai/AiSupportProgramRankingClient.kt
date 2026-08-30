package ai.govbiz.core.supportprogram.client.ai

import ai.govbiz.core.supportprogram.dto.ai.AiSupportProgramRankingPayload
import ai.govbiz.core.supportprogram.dto.ai.AiSupportProgramRankingRequest

fun interface AiSupportProgramRankingClient {
    fun rankSupportPrograms(
        request: AiSupportProgramRankingRequest,
    ): AiSupportProgramRankingPayload
}
