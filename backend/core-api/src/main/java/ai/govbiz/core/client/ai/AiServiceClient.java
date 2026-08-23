package ai.govbiz.core.client.ai;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiServiceClient {

    private static final String HEALTH_PATH = "/internal/v1/health";

    private final RestClient restClient;

    public AiServiceClient(@Qualifier("aiServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public AiServiceHealthPayload getHealth() {
        try {
            ResponseEntity<AiServiceHealthPayload> response = restClient.get()
                    .uri(HEALTH_PATH)
                    .retrieve()
                    .onStatus(
                            statusCode -> statusCode.value() != HttpStatus.OK.value(),
                            (request, clientResponse) -> {
                                int statusCode = clientResponse.getStatusCode().value();
                                if (statusCode == HttpStatus.NO_CONTENT.value()) {
                                    throw AiServiceClientException.invalidResponse(
                                            "AI Service returned HTTP 204 without a health response",
                                            null);
                                }
                                throw AiServiceClientException.upstreamError(
                                        "AI Service returned unexpected HTTP " + statusCode,
                                        null);
                            })
                    .toEntity(AiServiceHealthPayload.class);

            if (response.getBody() == null) {
                throw AiServiceClientException.invalidResponse(
                        "AI Service returned an empty health response",
                        null);
            }
            return response.getBody();
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw AiServiceClientException.timeout(exception);
            }
            throw AiServiceClientException.unavailable(exception);
        } catch (RestClientResponseException exception) {
            throw AiServiceClientException.upstreamError(
                    "AI Service returned HTTP " + exception.getStatusCode().value(),
                    exception);
        } catch (RestClientException exception) {
            throw AiServiceClientException.invalidResponse(
                    "AI Service response could not be decoded",
                    exception);
        }
    }

    private static boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
