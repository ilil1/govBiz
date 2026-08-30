package ai.govbiz.core.supportprogram.client.bizinfo

import ai.govbiz.core._common.http.executeHttpCall
import tools.jackson.core.JacksonException

/** 공통 HTTP 분류를 기업마당 공개 오류 계약에 맞게 변환합니다. */
internal fun <T> executeBizInfoCall(block: () -> T): T =
    try {
        executeHttpCall(
            onTimeout = { exception -> BizInfoClientException.timeout(exception) },
            onUnavailable = { exception -> BizInfoClientException.unavailable(exception) },
            onUpstreamError = { exception ->
                BizInfoClientException.upstreamError(
                    "BizInfo API returned HTTP ${exception.statusCode.value()}",
                    exception,
                )
            },
            onInvalidResponse = { exception ->
                BizInfoClientException.invalidResponse(
                    "BizInfo API response could not be decoded",
                    exception,
                )
            },
            block = block,
        )
    } catch (exception: JacksonException) {
        throw BizInfoClientException.invalidResponse(
            "BizInfo API response could not be decoded",
            exception,
        )
    } catch (exception: IllegalArgumentException) {
        throw BizInfoClientException.invalidResponse(
            "BizInfo API response could not be decoded",
            exception,
        )
    }
