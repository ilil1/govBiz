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

    fun failure(): Failure = failure

    companion object {
        @JvmStatic
        fun upstreamError(message: String, cause: Throwable?): AiServiceClientException =
            AiServiceClientException(Failure.UPSTREAM_ERROR, message, cause)

        @JvmStatic
        fun invalidResponse(message: String, cause: Throwable?): AiServiceClientException =
            AiServiceClientException(Failure.INVALID_RESPONSE, message, cause)

        @JvmStatic
        fun unavailable(cause: Throwable?): AiServiceClientException =
            AiServiceClientException(
                Failure.UNAVAILABLE,
                "AI Service could not be reached",
                cause,
            )

        @JvmStatic
        fun timeout(cause: Throwable?): AiServiceClientException =
            AiServiceClientException(
                Failure.TIMEOUT,
                "AI Service request timed out",
                cause,
            )
    }
}
