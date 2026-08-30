package ai.govbiz.core.supportprogram.client.ai

import ai.govbiz.core._common.http.hasTimeoutCause
import ai.govbiz.core.supportprogram.dto.ai.AiSupportProgramRankingPayload
import ai.govbiz.core.supportprogram.dto.ai.AiSupportProgramRankingRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/** 지원사업 후보의 AI 점수화 API를 호출하는 HTTP 어댑터입니다. */
@Component
class HttpAiSupportProgramRankingClient(
    @param:Qualifier("aiServiceRestClient") private val restClient: RestClient,
) : AiSupportProgramRankingClient {
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
                            throw AiSupportProgramRankingClientException.invalidResponse(
                                "AI Service returned HTTP 204 without support program rankings",
                                null,
                            )
                        }
                        if (statusCode == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                            throw AiSupportProgramRankingClientException.unavailable(null)
                        }
                        if (
                            statusCode == HttpStatus.REQUEST_TIMEOUT.value() ||
                            statusCode == HttpStatus.GATEWAY_TIMEOUT.value()
                        ) {
                            throw AiSupportProgramRankingClientException.timeout(null)
                        }
                        throw AiSupportProgramRankingClientException.upstreamError(
                            "AI Service returned unexpected HTTP $statusCode",
                            null,
                        )
                    },
                )
                .toEntity(AiSupportProgramRankingPayload::class.java)

            return response.body
                ?: throw AiSupportProgramRankingClientException.invalidResponse(
                    "AI Service returned an empty support program ranking response",
                    null,
                )
        } catch (exception: ResourceAccessException) {
            if (exception.hasTimeoutCause()) {
                throw AiSupportProgramRankingClientException.timeout(exception)
            }
            throw AiSupportProgramRankingClientException.unavailable(exception)
        } catch (exception: RestClientResponseException) {
            throw AiSupportProgramRankingClientException.upstreamError(
                "AI Service returned HTTP ${exception.statusCode.value()}",
                exception,
            )
        } catch (exception: RestClientException) {
            throw AiSupportProgramRankingClientException.invalidResponse(
                "AI Service support program ranking response could not be decoded",
                exception,
            )
        }
    }

    private companion object {
        const val SUPPORT_PROGRAM_RANKING_PATH =
            "/internal/v1/support-program-rankings/rank"
    }
}
