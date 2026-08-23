package ai.govbiz.core.dto.sample;

import ai.govbiz.core.domain.sample.ProcessingStatus;
import ai.govbiz.core.domain.sample.SampleCategory;
import ai.govbiz.core.domain.sample.SampleItemPhase;

/** 예제 준비 API가 소유하는 공개 응답 DTO입니다. */
public record SampleItemPreparationResponse(
        SampleItemPhase phase,
        Item item,
        Processing processing
) {

    public record Item(String name, SampleCategory category, String note) {
    }

    public record Processing(ProcessingStatus status) {
    }
}
