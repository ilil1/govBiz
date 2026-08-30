package ai.govbiz.core._adapters.ai.client

import ai.govbiz.core.supportprogram.client.ai.AiSupportProgramRankingClient
import ai.govbiz.core.supportprogram.dto.ai.AiSupportProgramRankingPayload
import ai.govbiz.core.supportprogram.dto.ai.AiSupportProgramRankingRequest
import ai.govbiz.core._common.http.hasTimeoutCause
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
    @param:Qualifier("aiServiceRestClient") private val restClient: RestClient,
) : AiSupportProgramRankingClient {

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
            if (exception.hasTimeoutCause()) {
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

    override fun rankSupportPrograms(
        request: AiSupportProgramRankingRequest,
    ): AiSupportProgramRankingPayload {
        try {
            val response = restClient.post()
                .uri(SUPPORT_PROGRAM_RANKING_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(
                    { statusCode -> statusCode.value() != HttpStatus.OK.value() },
                    { _, clientResponse ->
                        val statusCode = clientResponse.statusCode.value()
                        if (statusCode == HttpStatus.NO_CONTENT.value()) {
                            throw AiServiceClientException.invalidResponse(
                                "AI Service returned HTTP 204 without support program rankings",
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
                .toEntity(AiSupportProgramRankingPayload::class.java)

            return response.body
                ?: throw AiServiceClientException.invalidResponse(
                    "AI Service returned an empty support program ranking response",
                    null,
                )
        } catch (exception: ResourceAccessException) {
            if (exception.hasTimeoutCause()) {
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
                "AI Service support program ranking response could not be decoded",
                exception,
            )
        }
    }

    companion object {
        private const val HEALTH_PATH = "/internal/v1/health"
        private const val SUPPORT_PROGRAM_RANKING_PATH =
            "/internal/v1/support-program-rankings/rank"
    }
}
