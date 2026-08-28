package ai.govbiz.core.client.bizinfo

class BizInfoClientException private constructor(
    val failure: Failure,
    message: String,
    cause: Throwable?,
) : RuntimeException(message, cause) {

    enum class Failure {
        NOT_CONFIGURED,
        UPSTREAM_ERROR,
        INVALID_RESPONSE,
        UNAVAILABLE,
        TIMEOUT,
    }

    fun failure(): Failure = failure

    companion object {
        @JvmStatic
        fun notConfigured(): BizInfoClientException =
            BizInfoClientException(
                Failure.NOT_CONFIGURED,
                "BizInfo service key is not configured",
                null,
            )

        @JvmStatic
        fun upstreamError(message: String, cause: Throwable?): BizInfoClientException =
            BizInfoClientException(Failure.UPSTREAM_ERROR, message, cause)

        @JvmStatic
        fun invalidResponse(message: String, cause: Throwable?): BizInfoClientException =
            BizInfoClientException(Failure.INVALID_RESPONSE, message, cause)

        @JvmStatic
        fun unavailable(cause: Throwable?): BizInfoClientException =
            BizInfoClientException(
                Failure.UNAVAILABLE,
                "BizInfo API could not be reached",
                cause,
            )

        @JvmStatic
        fun timeout(cause: Throwable?): BizInfoClientException =
            BizInfoClientException(
                Failure.TIMEOUT,
                "BizInfo API request timed out",
                cause,
            )
    }
}
