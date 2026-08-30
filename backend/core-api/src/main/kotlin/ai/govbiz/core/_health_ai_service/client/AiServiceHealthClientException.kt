package ai.govbiz.core._health_ai_service.client

import ai.govbiz.core._common.exception.AiServiceFailure

class AiServiceHealthClientException private constructor(
    val failure: AiServiceFailure,
    message: String,
    cause: Throwable?,
) : RuntimeException(message, cause) {
    companion object {
        fun upstreamError(message: String, cause: Throwable?): AiServiceHealthClientException =
            AiServiceHealthClientException(AiServiceFailure.UPSTREAM_ERROR, message, cause)

        fun invalidResponse(message: String, cause: Throwable?): AiServiceHealthClientException =
            AiServiceHealthClientException(AiServiceFailure.INVALID_RESPONSE, message, cause)

        fun unavailable(cause: Throwable?): AiServiceHealthClientException =
            AiServiceHealthClientException(
                AiServiceFailure.UNAVAILABLE,
                "AI Service could not be reached",
                cause,
            )

        fun timeout(cause: Throwable?): AiServiceHealthClientException =
            AiServiceHealthClientException(
                AiServiceFailure.TIMEOUT,
                "AI Service request timed out",
                cause,
            )
    }
}
