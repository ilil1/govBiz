package ai.govbiz.core.service

data class AnalyzedSearchIntent(
    val keywords: List<String>,
    val regions: List<String>,
    val categories: List<String>,
    val targetTerms: List<String>,
    val clarificationNeeded: Boolean,
    val clarificationQuestion: String?,
)
