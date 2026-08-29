package ai.govbiz.core.client.ai

class AiServiceClientException private constructor(
    val failure: Failure,
    message: String,
    cause: Throwable?,
) : RuntimeException(message, cause) {

    enum class Failure {
        UPSTREAM_ERROR,
        INVALID_RESPONSE,
        UNAVAILABLE,
        TIMEOUT,
    }

    companion object {
        fun upstreamError(message: String, cause: Throwable?): AiServiceClientException =
            AiServiceClientException(Failure.UPSTREAM_ERROR, message, cause)

        fun invalidResponse(message: String, cause: Throwable?): AiServiceClientException =
            AiServiceClientException(Failure.INVALID_RESPONSE, message, cause)

        fun unavailable(cause: Throwable?): AiServiceClientException =
            AiServiceClientException(
                Failure.UNAVAILABLE,
                "AI Service could not be reached",
                cause,
            )

        fun timeout(cause: Throwable?): AiServiceClientException =
            AiServiceClientException(
                Failure.TIMEOUT,
                "AI Service request timed out",
                cause,
            )
    }
}
