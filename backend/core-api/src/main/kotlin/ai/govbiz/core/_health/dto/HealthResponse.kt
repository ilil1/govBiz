package ai.govbiz.core._health.dto

data class HealthResponse(
    val status: String,
    val service: String,
) {
    companion object {
        fun up(service: String): HealthResponse = HealthResponse("up", service)
    }
}
