import { prepareSampleItemApi } from '../api/sampleItemApi'
import type { SampleItemPreparationDto } from '../models/SampleItemDto'
import type { SampleItemPreparation } from '../../domain/entities/SampleItemPreparation'
import type {
  PrepareSampleItemCommand,
  SampleItemRepository,
} from '../../domain/repositories/SampleItemRepository'

/** Data 계층의 Core API 구현체입니다. DTO를 Domain 결과로 명시적으로 변환합니다. */
export class SampleItemRepositoryImpl implements SampleItemRepository {
  async prepare(
    command: PrepareSampleItemCommand,
    signal?: AbortSignal,
  ): Promise<SampleItemPreparation> {
    return toDomain(await prepareSampleItemApi(command, signal))
  }
}

function toDomain(response: SampleItemPreparationDto): SampleItemPreparation {
  return {
    phase: response.phase,
    item: response.item,
    processing: response.processing,
  }
}
