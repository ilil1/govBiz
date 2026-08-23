package io.basearchitecture.core.dto.sample;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 예제 준비 API의 공개 요청 DTO입니다. */
public record SampleItemPreparationRequest(
        @NotNull @Valid SampleItemRequest item
) {
}
