package ai.govbiz.core.client.ai

data class AiSearchIntentRequest(
    val query: String,
    val acceptingOnly: Boolean,
)
