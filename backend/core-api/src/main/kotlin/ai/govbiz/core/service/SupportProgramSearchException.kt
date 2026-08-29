package ai.govbiz.core.service

import ai.govbiz.core.client.bizinfo.BizInfoClientException

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
        internal fun fromClient(exception: BizInfoClientException): SupportProgramSearchException {
            val failure = when (exception.failure) {
                BizInfoClientException.Failure.NOT_CONFIGURED -> Failure.NOT_CONFIGURED
                BizInfoClientException.Failure.UPSTREAM_ERROR -> Failure.UPSTREAM_ERROR
                BizInfoClientException.Failure.INVALID_RESPONSE -> Failure.INVALID_RESPONSE
                BizInfoClientException.Failure.UNAVAILABLE -> Failure.UNAVAILABLE
                BizInfoClientException.Failure.TIMEOUT -> Failure.TIMEOUT
            }
            return SupportProgramSearchException(failure, exception.message, exception)
        }
    }
}
