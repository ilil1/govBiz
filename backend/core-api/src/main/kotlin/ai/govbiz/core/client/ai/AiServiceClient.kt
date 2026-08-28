package ai.govbiz.core.client.ai

import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.util.concurrent.TimeoutException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

@Component
class AiServiceClient(
    @Qualifier("aiServiceRestClient") private val restClient: RestClient,
) {

    fun getHealth(): AiServiceHealthPayload? {
        try {
            val response = restClient.get()
                .uri(HEALTH_PATH)
                .retrieve()
                .onStatus(
                    { statusCode -> statusCode.value() != HttpStatus.OK.value() },
                    { _, clientResponse ->
                        val statusCode = clientResponse.statusCode.value()
                        if (statusCode == HttpStatus.NO_CONTENT.value()) {
                            throw AiServiceClientException.invalidResponse(
                                "AI Service returned HTTP 204 without a health response",
                                null,
                            )
                        }
                        throw AiServiceClientException.upstreamError(
                            "AI Service returned unexpected HTTP $statusCode",
                            null,
                        )
                    },
                )
                .toEntity(AiServiceHealthPayload::class.java)

            return response.body
                ?: throw AiServiceClientException.invalidResponse(
                    "AI Service returned an empty health response",
                    null,
                )
        } catch (exception: ResourceAccessException) {
            if (hasTimeoutCause(exception)) {
                throw AiServiceClientException.timeout(exception)
            }
            throw AiServiceClientException.unavailable(exception)
        } catch (exception: RestClientResponseException) {
            throw AiServiceClientException.upstreamError(
                "AI Service returned HTTP ${exception.statusCode.value()}",
                exception,
            )
        } catch (exception: RestClientException) {
            throw AiServiceClientException.invalidResponse(
                "AI Service response could not be decoded",
                exception,
            )
        }
    }

    fun analyzeSearchIntent(query: String, acceptingOnly: Boolean): AiSearchIntentPayload? {
        try {
            val response = restClient.post()
                .uri(SEARCH_INTENT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AiSearchIntentRequest(query, acceptingOnly))
                .retrieve()
                .onStatus(
                    { statusCode -> statusCode.value() != HttpStatus.OK.value() },
                    { _, clientResponse ->
                        val statusCode = clientResponse.statusCode.value()
                        if (statusCode == HttpStatus.NO_CONTENT.value()) {
                            throw AiServiceClientException.invalidResponse(
                                "AI Service returned HTTP 204 without a search intent",
                                null,
                            )
                        }
                        if (statusCode == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                            throw AiServiceClientException.unavailable(null)
                        }
                        if (
                            statusCode == HttpStatus.REQUEST_TIMEOUT.value() ||
                            statusCode == HttpStatus.GATEWAY_TIMEOUT.value()
                        ) {
                            throw AiServiceClientException.timeout(null)
                        }
                        throw AiServiceClientException.upstreamError(
                            "AI Service returned unexpected HTTP $statusCode",
                            null,
                        )
                    },
                )
                .toEntity(AiSearchIntentPayload::class.java)

            return response.body
                ?: throw AiServiceClientException.invalidResponse(
                    "AI Service returned an empty search intent response",
                    null,
                )
        } catch (exception: ResourceAccessException) {
            if (hasTimeoutCause(exception)) {
                throw AiServiceClientException.timeout(exception)
            }
            throw AiServiceClientException.unavailable(exception)
        } catch (exception: RestClientResponseException) {
            throw AiServiceClientException.upstreamError(
                "AI Service returned HTTP ${exception.statusCode.value()}",
                exception,
            )
        } catch (exception: RestClientException) {
            throw AiServiceClientException.invalidResponse(
                "AI Service search intent response could not be decoded",
                exception,
            )
        }
    }

    private fun hasTimeoutCause(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
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

    companion object {
        private const val HEALTH_PATH = "/internal/v1/health"
        private const val SEARCH_INTENT_PATH = "/internal/v1/search-intents/analyze"
    }
}
