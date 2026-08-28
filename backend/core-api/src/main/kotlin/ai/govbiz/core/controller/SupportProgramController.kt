package ai.govbiz.core.controller

import ai.govbiz.core.dto.support.SupportProgramSearchResponse
import ai.govbiz.core.service.SupportProgramSearchService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/support-programs")
class SupportProgramController(
    private val searchService: SupportProgramSearchService,
) {

    @GetMapping("/search")
    fun search(
        @RequestParam query: String,
        @RequestParam(defaultValue = "true") acceptingOnly: Boolean,
    ): SupportProgramSearchResponse =
        SupportProgramSearchResponse.from(searchService.search(query, acceptingOnly))
}
