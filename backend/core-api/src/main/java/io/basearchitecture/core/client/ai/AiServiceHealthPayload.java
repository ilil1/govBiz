package io.basearchitecture.core.client.ai;

/** AI Service 내부 Health HTTP 계약입니다. 브라우저 공개 응답 DTO와 분리합니다. */
public record AiServiceHealthPayload(String status, String service) {
}
