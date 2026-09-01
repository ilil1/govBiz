package ai.govbiz.core.supportprogram.service.search

import ai.govbiz.core.supportprogram.service.dto.CatalogSupportProgram

/** 검색 유스케이스에 지원사업 후보를 제공하는 규격입니다. */
interface SupportProgramCatalog {
    fun load(): List<CatalogSupportProgram>
}
