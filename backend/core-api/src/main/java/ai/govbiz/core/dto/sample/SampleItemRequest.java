package ai.govbiz.core.dto.sample;

import ai.govbiz.core.domain.sample.SampleCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 샘플 항목의 공개 입력 DTO입니다. */
public record SampleItemRequest(
        @NotBlank @Size(max = 100) String name,
        SampleCategory category,
        @Size(max = 500) String note
) {
}
