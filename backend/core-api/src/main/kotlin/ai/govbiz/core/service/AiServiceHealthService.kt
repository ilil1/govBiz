package ai.govbiz.core.service

import ai.govbiz.core.client.ai.AiServiceClient
import ai.govbiz.core.client.ai.AiServiceClientException
import org.springframework.stereotype.Service

@Service
class AiServiceHealthService(
    private val aiServiceClient: AiServiceClient,
) {
    fun getHealth(): AiServiceHealthResult {
        val payload = try {
            aiServiceClient.getHealth()
        } catch (exception: AiServiceClientException) {
            throw AiServiceHealthException.fromClient(exception)
        }

        val status = payload.status
        val service = payload.service
        if (status != EXPECTED_STATUS || service != EXPECTED_SERVICE) {
            throw AiServiceHealthException.invalidContract()
        }

        return AiServiceHealthResult(status, service)
    }

    private companion object {
        const val EXPECTED_STATUS = "up"
        const val EXPECTED_SERVICE = "govbiz-ai-service"
    }
}
