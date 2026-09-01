package ai.govbiz.core.supportprogram.service.catalog

import ai.govbiz.core.supportprogram.service.dto.CatalogSupportProgram

interface SupportProgramCatalog {
    fun load(): List<CatalogSupportProgram>
}
