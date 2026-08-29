package ai.govbiz.core.service

import ai.govbiz.core.client.ai.AiSearchIntentPayload
import ai.govbiz.core.client.ai.AiServiceClient
import ai.govbiz.core.client.ai.AiServiceClientException
import ai.govbiz.core.text.isBlankLikeJava
import ai.govbiz.core.text.trimLikeJava
import org.springframework.stereotype.Service
import java.util.LinkedHashSet

/** 필수 AI Service 응답을 검증하고 검색 서비스가 사용할 의도로 변환한다. */
@Service
class AiSearchIntentService(
    private val client: AiServiceClient,
) {
    fun analyze(query: String?, acceptingOnly: Boolean): AnalyzedSearchIntent {
        val normalizedQuery = query?.trimLikeJava().orEmpty()
        require(!normalizedQuery.isBlankLikeJava()) { "query must not be blank" }

        val payload = client.analyzeSearchIntent(normalizedQuery, acceptingOnly)
        return validate(payload, normalizedQuery, acceptingOnly)
            ?: throw AiServiceClientException.invalidResponse(
                "AI Service search intent violated the internal contract",
                null,
            )
    }

    private fun validate(
        payload: AiSearchIntentPayload,
        expectedQuery: String,
        expectedAcceptingOnly: Boolean,
    ): AnalyzedSearchIntent? {
        if (payload.originalQuery != expectedQuery ||
            payload.acceptingOnly == null ||
            payload.acceptingOnly != expectedAcceptingOnly ||
            payload.clarificationNeeded == null
        ) {
            return null
        }

        val keywords = terms(payload.keywords, null) ?: return null
        val regions = terms(payload.regions, ALLOWED_REGIONS) ?: return null
        val categories = terms(payload.categories, ALLOWED_CATEGORIES) ?: return null
        val targetTerms = terms(payload.targetTerms, null) ?: return null

        val clarificationQuestion = trimToNull(payload.clarificationQuestion)
        if (payload.clarificationNeeded == true) {
            if (clarificationQuestion == null ||
                clarificationQuestion.length > MAX_CLARIFICATION_LENGTH
            ) {
                return null
            }
        } else if (clarificationQuestion != null) {
            return null
        }

        return AnalyzedSearchIntent(
            keywords = keywords,
            regions = regions,
            categories = categories,
            targetTerms = targetTerms,
            clarificationNeeded = payload.clarificationNeeded,
            clarificationQuestion = clarificationQuestion,
        )
    }

    private fun terms(values: List<String?>?, allowed: Set<String>?): List<String>? {
        if (values == null || values.size > MAX_TERMS_PER_FIELD) return null

        val normalized = LinkedHashSet<String>()
        for (value in values) {
            val term = trimToNull(value)
            if (term == null ||
                term.length > MAX_TERM_LENGTH ||
                containsControlCharacter(term) ||
                (allowed != null && term !in allowed)
            ) {
                return null
            }
            normalized += term
        }
        return java.util.List.copyOf(normalized)
    }

    private fun trimToNull(value: String?): String? =
        value?.trimLikeJava()?.takeIf(String::isNotEmpty)

    private fun containsControlCharacter(value: String): Boolean =
        value.codePoints().anyMatch(Character::isISOControl)

    private companion object {
        const val MAX_TERMS_PER_FIELD = 8
        const val MAX_TERM_LENGTH = 60
        const val MAX_CLARIFICATION_LENGTH = 200

        val ALLOWED_REGIONS = setOf(
            "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
            "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주", "전국",
        )
        val ALLOWED_CATEGORIES = setOf(
            "AI", "창업", "기술", "수출", "경영", "금융", "인력", "내수",
            "제조", "콘텐츠", "소상공인",
        )
    }
}
