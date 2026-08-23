package io.basearchitecture.core.dto.sample;

import io.basearchitecture.core.domain.sample.ProcessingStatus;
import io.basearchitecture.core.domain.sample.SampleCategory;
import io.basearchitecture.core.domain.sample.SampleItemPhase;

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
