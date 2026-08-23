package ai.govbiz.core.service;

import ai.govbiz.core.domain.sample.ProcessingStatus;
import ai.govbiz.core.domain.sample.SampleItem;
import ai.govbiz.core.domain.sample.SampleItemPhase;
import ai.govbiz.core.domain.sample.SampleItemPreparation;
import org.springframework.stereotype.Service;

/** 검증된 예제 입력을 이후 처리 단계에 넘길 준비 상태로 만듭니다. */
@Service
public class SampleItemPreparationService {

    public SampleItemPreparation prepare(SampleItem item) {
        return new SampleItemPreparation(
                SampleItemPhase.READY_FOR_PROCESSING,
                item,
                ProcessingStatus.NOT_STARTED);
    }
}
