package ai.govbiz.core.service;

import ai.govbiz.core.client.bizinfo.BizInfoClientException;

public final class SupportProgramSearchException extends RuntimeException {

    public enum Failure {
        NOT_CONFIGURED,
        UPSTREAM_ERROR,
        INVALID_RESPONSE,
        UNAVAILABLE,
        TIMEOUT
    }

    private final Failure failure;

    private SupportProgramSearchException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    static SupportProgramSearchException fromClient(BizInfoClientException exception) {
        Failure failure = switch (exception.failure()) {
            case NOT_CONFIGURED -> Failure.NOT_CONFIGURED;
            case UPSTREAM_ERROR -> Failure.UPSTREAM_ERROR;
            case INVALID_RESPONSE -> Failure.INVALID_RESPONSE;
            case UNAVAILABLE -> Failure.UNAVAILABLE;
            case TIMEOUT -> Failure.TIMEOUT;
        };
        return new SupportProgramSearchException(failure, exception.getMessage(), exception);
    }

    public Failure failure() {
        return failure;
    }
}
