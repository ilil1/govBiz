package ai.govbiz.core.supportprogram.client.ai

import ai.govbiz.core._common.exception.AiServiceFailure

class AiSupportProgramRankingClientException private constructor(
    val failure: AiServiceFailure,
    message: String,
    cause: Throwable?,
) : RuntimeException(message, cause) {
    companion object {
        fun upstreamError(
            message: String,
            cause: Throwable?,
        ): AiSupportProgramRankingClientException =
            AiSupportProgramRankingClientException(
                AiServiceFailure.UPSTREAM_ERROR,
                message,
                cause,
            )

        fun invalidResponse(
            message: String,
            cause: Throwable?,
        ): AiSupportProgramRankingClientException =
            AiSupportProgramRankingClientException(
                AiServiceFailure.INVALID_RESPONSE,
                message,
                cause,
            )

        fun unavailable(cause: Throwable?): AiSupportProgramRankingClientException =
            AiSupportProgramRankingClientException(
                AiServiceFailure.UNAVAILABLE,
                "AI Service could not be reached",
                cause,
            )

        fun timeout(cause: Throwable?): AiSupportProgramRankingClientException =
            AiSupportProgramRankingClientException(
                AiServiceFailure.TIMEOUT,
                "AI Service request timed out",
                cause,
            )
    }
}
