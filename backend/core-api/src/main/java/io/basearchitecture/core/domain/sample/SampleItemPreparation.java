package io.basearchitecture.core.domain.sample;

import java.util.Objects;

/** 입력 검증을 통과한 예제 요청의 준비 결과입니다. */
public record SampleItemPreparation(
        SampleItemPhase phase,
        SampleItem item,
        ProcessingStatus processingStatus
) {

    public SampleItemPreparation {
        phase = Objects.requireNonNull(phase, "phase must not be null");
        item = Objects.requireNonNull(item, "item must not be null");
        if (processingStatus == null) {
            throw new IllegalArgumentException("processingStatus must not be null");
        }

        if (phase != SampleItemPhase.READY_FOR_PROCESSING
                || processingStatus != ProcessingStatus.NOT_STARTED) {
            throw new IllegalArgumentException(
                    "sample preparation must be READY_FOR_PROCESSING and NOT_STARTED");
        }
    }
}
