package ai.govbiz.core._common.http

import ai.govbiz.core._common.exception.AiServiceCallException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/** 두 AI HTTP Client가 공유하는 Spring 예외 변환만 담당합니다. */
fun <T> executeAiServiceCall(block: () -> T): T =
    try {
        block()
    } catch (exception: ResourceAccessException) {
        if (exception.hasTimeoutCause()) {
            throw AiServiceCallException.timeout(exception)
        }
        throw AiServiceCallException.unavailable(exception)
    } catch (exception: RestClientResponseException) {
        throw AiServiceCallException.upstreamError(
            "AI Service returned HTTP ${exception.statusCode.value()}",
            exception,
        )
    } catch (exception: RestClientException) {
        throw AiServiceCallException.invalidResponse(
            "AI Service response could not be decoded",
            exception,
        )
    }
