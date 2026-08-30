package ai.govbiz.core._common.http

import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.util.concurrent.TimeoutException

internal fun Throwable.hasTimeoutCause(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (
            current is HttpTimeoutException ||
            current is SocketTimeoutException ||
            current is TimeoutException
        ) {
            return true
        }
        current = current.cause
    }
    return false
}
