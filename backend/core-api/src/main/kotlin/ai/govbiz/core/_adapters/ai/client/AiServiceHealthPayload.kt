package ai.govbiz.core._adapters.ai.client

/** AI Service 내부 Health HTTP 계약입니다. 브라우저 공개 응답 DTO와 분리합니다. */
data class AiServiceHealthPayload(
    val status: String?,
    val service: String?,
)
