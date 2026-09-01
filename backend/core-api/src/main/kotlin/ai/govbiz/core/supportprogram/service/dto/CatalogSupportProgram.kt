package ai.govbiz.core.supportprogram.service.dto

import ai.govbiz.core.supportprogram.domain.SupportProgram

data class CatalogSupportProgram(
    val program: SupportProgram,
    val sortTimestamp: String,
)
