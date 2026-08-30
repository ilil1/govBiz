package ai.govbiz.core._health_ai_service.client

import ai.govbiz.core._common.http.hasTimeoutCause
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/** AI Service의 상태 확인 API만 호출하는 HTTP 클라이언트입니다. */
@Component
class AiServiceHealthClient(
    @param:Qualifier("aiServiceRestClient") private val restClient: RestClient,
) {
    fun getHealth(): AiServiceHealthPayload {
        try {
            val response = restClient.get()
                .uri(HEALTH_PATH)
                .retrieve()
                .onStatus(
                    { statusCode -> statusCode.value() != HttpStatus.OK.value() },
                    { _, clientResponse ->
                        val statusCode = clientResponse.statusCode.value()
                        if (statusCode == HttpStatus.NO_CONTENT.value()) {
                            throw AiServiceHealthClientException.invalidResponse(
                                "AI Service returned HTTP 204 without a health response",
                                null,
                            )
                        }
                        throw AiServiceHealthClientException.upstreamError(
                            "AI Service returned unexpected HTTP $statusCode",
                            null,
                        )
                    },
                )
                .toEntity(AiServiceHealthPayload::class.java)

            return response.body
                ?: throw AiServiceHealthClientException.invalidResponse(
                    "AI Service returned an empty health response",
                    null,
                )
        } catch (exception: ResourceAccessException) {
            if (exception.hasTimeoutCause()) {
                throw AiServiceHealthClientException.timeout(exception)
            }
            throw AiServiceHealthClientException.unavailable(exception)
        } catch (exception: RestClientResponseException) {
            throw AiServiceHealthClientException.upstreamError(
                "AI Service returned HTTP ${exception.statusCode.value()}",
                exception,
            )
        } catch (exception: RestClientException) {
            throw AiServiceHealthClientException.invalidResponse(
                "AI Service response could not be decoded",
                exception,
            )
        }
    }

    private companion object {
        const val HEALTH_PATH = "/internal/v1/health"
    }
}
