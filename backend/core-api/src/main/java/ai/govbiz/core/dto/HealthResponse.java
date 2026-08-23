package ai.govbiz.core.dto;

public record HealthResponse(String status, String service) {

    public static HealthResponse up(String service) {
        return new HealthResponse("up", service);
    }
}
