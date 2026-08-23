package ai.govbiz.core.service;

import ai.govbiz.core.domain.sample.ProcessingStatus;
import ai.govbiz.core.domain.sample.SampleCategory;
import ai.govbiz.core.domain.sample.SampleItem;
import ai.govbiz.core.domain.sample.SampleItemPhase;
import ai.govbiz.core.domain.sample.SampleItemPreparation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SampleItemPreparationServiceTest {

    private final SampleItemPreparationService service = new SampleItemPreparationService();

    @Test
    void createsAReadySnapshotWithoutStartingProcessing() {
        SampleItem item = new SampleItem(
                " Example item ",
                SampleCategory.EXTENDED,
                " A reusable example ");

        SampleItemPreparation result = service.prepare(item);

        assertEquals(SampleItemPhase.READY_FOR_PROCESSING, result.phase());
        assertEquals(ProcessingStatus.NOT_STARTED, result.processingStatus());
        assertSame(item, result.item());
        assertEquals("Example item", result.item().name());
    }

    @Test
    void rejectsStatesThatPretendProcessingHasAlreadyStarted() {
        SampleItem item = new SampleItem("Example", null, null);

        assertThrows(IllegalArgumentException.class, () -> new SampleItemPreparation(
                SampleItemPhase.READY_FOR_PROCESSING,
                item,
                null));
    }
}
