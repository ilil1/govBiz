import type { SampleItemPreparation } from '../entities/SampleItemPreparation'
import type { PrepareSampleItemCommand, SampleItemRepository } from '../repositories/SampleItemRepository'

/** UseCase는 HTTP 구현을 모르고 Repository port만 사용합니다. */
export function prepareSampleItem(
  repository: SampleItemRepository,
  command: PrepareSampleItemCommand,
  signal?: AbortSignal,
): Promise<SampleItemPreparation> {
  return repository.prepare(command, signal)
}
