package ai.govbiz.core.client.ai

/**
 * AI Service의 내부 검색 의도 계약이다. Boolean nullable 타입은 누락된 필드와 false를
 * 구분하기 위해 의도적으로 사용한다.
 */
data class AiSearchIntentPayload(
    val originalQuery: String?,
    val keywords: List<String?>?,
    val regions: List<String?>?,
    val categories: List<String?>?,
    val targetTerms: List<String?>?,
    val acceptingOnly: Boolean?,
    val clarificationNeeded: Boolean?,
    val clarificationQuestion: String?,
)
