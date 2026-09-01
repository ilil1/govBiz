package ai.govbiz.core.supportprogram.service.search

class SupportProgramSearchException private constructor(
    val failure: Failure,
    message: String?,
    cause: Throwable?,
) : RuntimeException(message, cause) {
    enum class Failure {
        NOT_CONFIGURED,
        UPSTREAM_ERROR,
        INVALID_RESPONSE,
        UNAVAILABLE,
        TIMEOUT,
    }

    companion object {
        internal fun fromCatalog(
            failure: Failure,
            message: String?,
            cause: Throwable,
        ): SupportProgramSearchException =
            SupportProgramSearchException(failure, message, cause)
    }
}
