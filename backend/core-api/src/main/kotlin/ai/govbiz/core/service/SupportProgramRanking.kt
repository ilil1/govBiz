package ai.govbiz.core.service

import ai.govbiz.core.domain.support.SupportProgram

/** 검색 유스케이스가 필요로 하는 공고 후보 점수화 포트. */
fun interface SupportProgramRanking {
    fun rank(
        query: String,
        candidates: List<CatalogSupportProgram>,
        limit: Int,
    ): List<SupportProgram>

    companion object {
        const val MAX_CANDIDATES = 20
        const val MAX_RESULTS = 5
    }
}
