package ai.govbiz.core.dto

data class HealthResponse(
    val status: String,
    val service: String,
) {
    companion object {
        @JvmStatic
        fun up(service: String): HealthResponse = HealthResponse("up", service)
    }
}
