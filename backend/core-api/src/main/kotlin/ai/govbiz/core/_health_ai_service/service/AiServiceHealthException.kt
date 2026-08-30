package ai.govbiz.core._health_ai_service.service

import ai.govbiz.core._common.exception.AiServiceFailure
import ai.govbiz.core._health_ai_service.client.AiServiceHealthClientException

class AiServiceHealthException private constructor(
    val failure: AiServiceFailure,
    message: String?,
    cause: Throwable?,
) : RuntimeException(message, cause) {
    companion object {
        internal fun fromClient(exception: AiServiceHealthClientException): AiServiceHealthException =
            AiServiceHealthException(exception.failure, exception.message, exception)

        internal fun invalidContract(): AiServiceHealthException =
            AiServiceHealthException(
                AiServiceFailure.INVALID_RESPONSE,
                "AI Service health response violated the expected contract",
                null,
            )
    }
}
