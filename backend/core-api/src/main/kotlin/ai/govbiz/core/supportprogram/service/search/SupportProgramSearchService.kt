package ai.govbiz.core.supportprogram.service.search

import ai.govbiz.core.supportprogram.domain.SupportProgramStatus
import ai.govbiz.core.supportprogram.service.dto.CatalogSupportProgram
import ai.govbiz.core.supportprogram.service.dto.SupportProgramSearchResult
import ai.govbiz.core.supportprogram.service.ranking.SupportProgramRanking
import org.springframework.stereotype.Service

/** 공식 공고 후보와 LLM 점수화를 연결하는 검색 유스케이스입니다. */
@Service
class SupportProgramSearchService(
    private val catalog: SupportProgramCatalog,
    private val ranking: SupportProgramRanking,
) {
    fun search(rawQuery: String?, acceptingOnly: Boolean): SupportProgramSearchResult {
        val query = rawQuery?.trim().orEmpty()
        val candidates = catalog.load()
            .asSequence()
            .filter { !acceptingOnly || it.program.status == SupportProgramStatus.OPEN }
            .sortedByDescending(CatalogSupportProgram::sortTimestamp)
            .take(SupportProgramRanking.MAX_CANDIDATES)
            .toList()

        val programs = when {
            candidates.isEmpty() -> emptyList()
            query.isBlank() -> candidates
                .take(SupportProgramRanking.MAX_RESULTS)
                .map { it.program.copy(matchedReasons = emptyList(), recommendationScore = null) }
            else -> ranking.rank(query, candidates, SupportProgramRanking.MAX_RESULTS)
        }

        return SupportProgramSearchResult(
            query = query,
            programs = java.util.List.copyOf(programs),
        )
    }
}
