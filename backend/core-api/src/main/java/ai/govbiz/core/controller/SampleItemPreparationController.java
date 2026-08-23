package ai.govbiz.core.controller;

import ai.govbiz.core.domain.sample.SampleItem;
import ai.govbiz.core.domain.sample.SampleItemPreparation;
import ai.govbiz.core.dto.sample.SampleItemPreparationRequest;
import ai.govbiz.core.dto.sample.SampleItemPreparationResponse;
import ai.govbiz.core.dto.sample.SampleItemRequest;
import ai.govbiz.core.service.SampleItemPreparationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 프런트엔드 Data Layer와 Core API 수직 슬라이스를 보여 주는 공개 예제 API입니다. */
@RestController
@RequestMapping("/api/v1/sample-items")
public class SampleItemPreparationController {

    private final SampleItemPreparationService preparationService;

    public SampleItemPreparationController(SampleItemPreparationService preparationService) {
        this.preparationService = preparationService;
    }

    @PostMapping(
            path = "/prepare",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public SampleItemPreparationResponse prepare(
            @Valid @RequestBody SampleItemPreparationRequest request
    ) {
        SampleItemPreparation preparation = preparationService.prepare(toSampleItem(request.item()));
        return toResponse(preparation);
    }

    private SampleItem toSampleItem(SampleItemRequest request) {
        return new SampleItem(request.name(), request.category(), request.note());
    }

    private SampleItemPreparationResponse toResponse(SampleItemPreparation preparation) {
        SampleItem item = preparation.item();
        return new SampleItemPreparationResponse(
                preparation.phase(),
                new SampleItemPreparationResponse.Item(item.name(), item.category(), item.note()),
                new SampleItemPreparationResponse.Processing(preparation.processingStatus()));
    }
}
