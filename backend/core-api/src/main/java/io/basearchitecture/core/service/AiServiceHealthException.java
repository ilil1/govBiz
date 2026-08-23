package io.basearchitecture.core.service;

import io.basearchitecture.core.client.ai.AiServiceClientException;

public final class AiServiceHealthException extends RuntimeException {

    public enum Failure {
        UPSTREAM_ERROR,
        INVALID_RESPONSE,
        UNAVAILABLE,
        TIMEOUT
    }

    private final Failure failure;

    private AiServiceHealthException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    static AiServiceHealthException fromClient(AiServiceClientException exception) {
        Failure failure = switch (exception.failure()) {
            case UPSTREAM_ERROR -> Failure.UPSTREAM_ERROR;
            case INVALID_RESPONSE -> Failure.INVALID_RESPONSE;
            case UNAVAILABLE -> Failure.UNAVAILABLE;
            case TIMEOUT -> Failure.TIMEOUT;
        };
        return new AiServiceHealthException(failure, exception.getMessage(), exception);
    }

    static AiServiceHealthException invalidContract() {
        return new AiServiceHealthException(
                Failure.INVALID_RESPONSE,
                "AI Service health response violated the expected contract",
                null);
    }

    public Failure failure() {
        return failure;
    }
}
