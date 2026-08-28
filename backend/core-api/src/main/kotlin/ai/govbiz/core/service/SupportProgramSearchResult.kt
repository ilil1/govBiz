package ai.govbiz.core.service

import ai.govbiz.core.domain.support.SupportProgram

data class SupportProgramSearchResult(
    val query: String,
    val programs: List<SupportProgram>,
)
