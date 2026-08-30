package ai.govbiz.core.service

import org.springframework.stereotype.Service

/** AI 검색 의도, 공고 카탈로그와 순위 계산을 연결하는 검색 유스케이스입니다. */
@Service
class SupportProgramSearchService(
    private val aiSearchIntentService: AiSearchIntentService,
    private val catalog: SupportProgramCatalog,
    private val ranker: SupportProgramRanker,
) {
    fun search(rawQuery: String?, acceptingOnly: Boolean): SupportProgramSearchResult {
        val query = rawQuery?.trim().orEmpty()
        val analyzedIntent = if (query.isBlank()) {
            null
        } else {
            aiSearchIntentService.analyze(query, acceptingOnly)
        }

        return SupportProgramSearchResult(
            query = query,
            programs = java.util.List.copyOf(
                ranker.rank(
                    candidates = catalog.load(),
                    analyzedIntent = analyzedIntent,
                    acceptingOnly = acceptingOnly,
                    limit = RESULT_LIMIT,
                ),
            ),
        )
    }

    private companion object {
        const val RESULT_LIMIT = 5
    }
}
