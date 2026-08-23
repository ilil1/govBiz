package ai.govbiz.core.service;

import ai.govbiz.core.client.ai.AiServiceClient;
import ai.govbiz.core.client.ai.AiServiceClientException;
import ai.govbiz.core.client.ai.AiServiceHealthPayload;
import org.springframework.stereotype.Service;

@Service
public class AiServiceHealthService {

    private static final String EXPECTED_STATUS = "up";
    private static final String EXPECTED_SERVICE = "govbiz-ai-service";

    private final AiServiceClient aiServiceClient;

    public AiServiceHealthService(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    public AiServiceHealthResult getHealth() {
        AiServiceHealthPayload payload;
        try {
            payload = aiServiceClient.getHealth();
        } catch (AiServiceClientException exception) {
            throw AiServiceHealthException.fromClient(exception);
        }

        if (payload == null
                || !EXPECTED_STATUS.equals(payload.status())
                || !EXPECTED_SERVICE.equals(payload.service())) {
            throw AiServiceHealthException.invalidContract();
        }

        return new AiServiceHealthResult(payload.status(), payload.service());
    }
}
