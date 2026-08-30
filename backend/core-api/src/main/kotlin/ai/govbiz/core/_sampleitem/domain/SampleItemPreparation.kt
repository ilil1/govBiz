package ai.govbiz.core._sampleitem.domain

/** 입력 검증을 통과한 예제 요청의 준비 결과입니다. */
class SampleItemPreparation(
    phase: SampleItemPhase?,
    item: SampleItem?,
    processingStatus: ProcessingStatus?,
) {
    val phase: SampleItemPhase = phase ?: throw NullPointerException("phase must not be null")
    val item: SampleItem = item ?: throw NullPointerException("item must not be null")
    val processingStatus: ProcessingStatus = processingStatus
        ?: throw IllegalArgumentException("processingStatus must not be null")

    init {
        if (this.phase != SampleItemPhase.READY_FOR_PROCESSING ||
            this.processingStatus != ProcessingStatus.NOT_STARTED
        ) {
            throw IllegalArgumentException(
                "sample preparation must be READY_FOR_PROCESSING and NOT_STARTED",
            )
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is SampleItemPreparation &&
            phase == other.phase &&
            item == other.item &&
            processingStatus == other.processingStatus

    override fun hashCode(): Int {
        var result = phase.hashCode()
        result = 31 * result + item.hashCode()
        result = 31 * result + processingStatus.hashCode()
        return result
    }

    override fun toString(): String =
        "SampleItemPreparation[phase=$phase, item=$item, processingStatus=$processingStatus]"
}
