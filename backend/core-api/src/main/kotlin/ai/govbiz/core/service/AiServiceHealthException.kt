package ai.govbiz.core.service

import ai.govbiz.core.client.ai.AiServiceClientException

class AiServiceHealthException private constructor(
    val failure: AiServiceClientException.Failure,
    message: String?,
    cause: Throwable?,
) : RuntimeException(message, cause) {
    companion object {
        internal fun fromClient(exception: AiServiceClientException): AiServiceHealthException =
            AiServiceHealthException(exception.failure, exception.message, exception)

        internal fun invalidContract(): AiServiceHealthException =
            AiServiceHealthException(
                AiServiceClientException.Failure.INVALID_RESPONSE,
                "AI Service health response violated the expected contract",
                null,
            )
    }
}
