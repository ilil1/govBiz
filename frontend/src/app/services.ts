import { SampleItemRepositoryImpl } from '../data/repositories/SampleItemRepositoryImpl'
import { FixtureSupportProgramRepository } from '../data/repositories/FixtureSupportProgramRepository'
import { fetchCoreApiHealth } from '../data/core-api/fetchCoreApiHealth'
import type { CoreApiHealth } from '../data/core-api/coreApiHealth'
import type { SampleItemPreparation } from '../domain/entities/SampleItemPreparation'
import type { PrepareSampleItemCommand } from '../domain/repositories/SampleItemRepository'
import { prepareSampleItem } from '../domain/usecases/PrepareSampleItemUseCase'
import { SearchSupportProgramsUseCase } from '../domain/usecases/SearchSupportProgramsUseCase'

export type AppServices = {
  fetchCoreApiHealth(signal?: AbortSignal): Promise<CoreApiHealth>
  prepareSampleItem(
    command: PrepareSampleItemCommand,
    signal?: AbortSignal,
  ): Promise<SampleItemPreparation>
  searchSupportPrograms: Pick<SearchSupportProgramsUseCase, 'execute'>
}

/** 실제 구현체를 선택하는 프런트엔드 Composition Root입니다. */
export function createAppServices(): AppServices {
  const sampleItemRepository = new SampleItemRepositoryImpl()

  return {
    fetchCoreApiHealth,
    prepareSampleItem: prepareSampleItem.bind(null, sampleItemRepository),
    searchSupportPrograms: new SearchSupportProgramsUseCase(
      new FixtureSupportProgramRepository(),
    ),
  }
}
