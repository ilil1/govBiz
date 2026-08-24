package ai.govbiz.core.controller;

import ai.govbiz.core.dto.support.SupportProgramSearchResponse;
import ai.govbiz.core.service.SupportProgramSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support-programs")
public class SupportProgramController {

    private final SupportProgramSearchService searchService;

    public SupportProgramController(SupportProgramSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public SupportProgramSearchResponse search(
            @RequestParam String query,
            @RequestParam(defaultValue = "true") boolean acceptingOnly
    ) {
        return SupportProgramSearchResponse.from(searchService.search(query, acceptingOnly));
    }
}
