package ai.govbiz.core.client.bizinfo;

public final class BizInfoClientException extends RuntimeException {

    public enum Failure {
        NOT_CONFIGURED,
        UPSTREAM_ERROR,
        INVALID_RESPONSE,
        UNAVAILABLE,
        TIMEOUT
    }

    private final Failure failure;

    private BizInfoClientException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public static BizInfoClientException notConfigured() {
        return new BizInfoClientException(
                Failure.NOT_CONFIGURED,
                "BizInfo service key is not configured",
                null);
    }

    public static BizInfoClientException upstreamError(String message, Throwable cause) {
        return new BizInfoClientException(Failure.UPSTREAM_ERROR, message, cause);
    }

    public static BizInfoClientException invalidResponse(String message, Throwable cause) {
        return new BizInfoClientException(Failure.INVALID_RESPONSE, message, cause);
    }

    public static BizInfoClientException unavailable(Throwable cause) {
        return new BizInfoClientException(
                Failure.UNAVAILABLE,
                "BizInfo API could not be reached",
                cause);
    }

    public static BizInfoClientException timeout(Throwable cause) {
        return new BizInfoClientException(
                Failure.TIMEOUT,
                "BizInfo API request timed out",
                cause);
    }

    public Failure failure() {
        return failure;
    }
}
