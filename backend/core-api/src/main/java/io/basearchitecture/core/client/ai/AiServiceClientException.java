package io.basearchitecture.core.client.ai;

public final class AiServiceClientException extends RuntimeException {

    public enum Failure {
        UPSTREAM_ERROR,
        INVALID_RESPONSE,
        UNAVAILABLE,
        TIMEOUT
    }

    private final Failure failure;

    private AiServiceClientException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public static AiServiceClientException upstreamError(String message, Throwable cause) {
        return new AiServiceClientException(Failure.UPSTREAM_ERROR, message, cause);
    }

    public static AiServiceClientException invalidResponse(String message, Throwable cause) {
        return new AiServiceClientException(Failure.INVALID_RESPONSE, message, cause);
    }

    public static AiServiceClientException unavailable(Throwable cause) {
        return new AiServiceClientException(
                Failure.UNAVAILABLE,
                "AI Service could not be reached",
                cause);
    }

    public static AiServiceClientException timeout(Throwable cause) {
        return new AiServiceClientException(
                Failure.TIMEOUT,
                "AI Service request timed out",
                cause);
    }

    public Failure failure() {
        return failure;
    }
}
