package ai.govbiz.core.controller

import ai.govbiz.core.domain.sample.SampleItem
import ai.govbiz.core.domain.sample.SampleItemPreparation
import ai.govbiz.core.dto.sample.SampleItemPreparationRequest
import ai.govbiz.core.dto.sample.SampleItemPreparationResponse
import ai.govbiz.core.dto.sample.SampleItemRequest
import ai.govbiz.core.service.SampleItemPreparationService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 프런트엔드 Data Layer와 Core API 수직 슬라이스를 보여 주는 공개 예제 API입니다. */
@RestController
@RequestMapping("/api/v1/sample-items")
class SampleItemPreparationController(
    private val preparationService: SampleItemPreparationService,
) {

    @PostMapping(
        path = ["/prepare"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun prepare(
        @Valid @RequestBody request: SampleItemPreparationRequest,
    ): SampleItemPreparationResponse {
        val item = requireNotNull(request.item)
        val preparation = preparationService.prepare(toSampleItem(item))
        return toResponse(preparation)
    }

    private fun toSampleItem(request: SampleItemRequest): SampleItem =
        SampleItem(request.name, request.category, request.note)

    private fun toResponse(preparation: SampleItemPreparation): SampleItemPreparationResponse {
        val item = preparation.item
        return SampleItemPreparationResponse(
            preparation.phase,
            SampleItemPreparationResponse.Item(item.name, item.category, item.note),
            SampleItemPreparationResponse.Processing(preparation.processingStatus),
        )
    }
}
