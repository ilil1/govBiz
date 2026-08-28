package ai.govbiz.core.service

import ai.govbiz.core.client.ai.AiServiceClientException

class AiServiceHealthException private constructor(
    val failure: Failure,
    message: String?,
    cause: Throwable?,
) : RuntimeException(message, cause) {
    enum class Failure {
        UPSTREAM_ERROR,
        INVALID_RESPONSE,
        UNAVAILABLE,
        TIMEOUT,
    }

    fun failure(): Failure = failure

    companion object {
        internal fun fromClient(exception: AiServiceClientException): AiServiceHealthException {
            val failure = when (exception.failure) {
                AiServiceClientException.Failure.UPSTREAM_ERROR -> Failure.UPSTREAM_ERROR
                AiServiceClientException.Failure.INVALID_RESPONSE -> Failure.INVALID_RESPONSE
                AiServiceClientException.Failure.UNAVAILABLE -> Failure.UNAVAILABLE
                AiServiceClientException.Failure.TIMEOUT -> Failure.TIMEOUT
            }
            return AiServiceHealthException(failure, exception.message, exception)
        }

        internal fun invalidContract(): AiServiceHealthException =
            AiServiceHealthException(
                Failure.INVALID_RESPONSE,
                "AI Service health response violated the expected contract",
                null,
            )
    }
}
