package ai.govbiz.core.dto;

/** Core API가 소유하는 브라우저 공개 AI Service Health 응답입니다. */
public record AiServiceHealthResponse(String status, String service) {
}
