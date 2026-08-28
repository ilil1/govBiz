package ai.govbiz.core.dto.support

import ai.govbiz.core.service.SupportProgramSearchResult

data class SupportProgramSearchResponse(
    val query: String,
    val programs: List<SupportProgramResponse>,
) {
    companion object {
        @JvmStatic
        fun from(result: SupportProgramSearchResult): SupportProgramSearchResponse =
            SupportProgramSearchResponse(
                query = result.query,
                programs = java.util.List.copyOf(
                    result.programs.map(SupportProgramResponse::from),
                ),
            )
    }
}
