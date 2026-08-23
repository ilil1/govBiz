import type { SampleItem } from '../entities/SampleItem'
import type { SampleItemPreparation } from '../entities/SampleItemPreparation'

export type PrepareSampleItemCommand = {
  item: SampleItem
}

/** Domain이 요구하는 예제 준비 API의 포트입니다. */
export interface SampleItemRepository {
  prepare(
    command: PrepareSampleItemCommand,
    signal?: AbortSignal,
  ): Promise<SampleItemPreparation>
}
