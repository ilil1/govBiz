import { asFunction } from 'awilix/browser'

import { prepareSampleItem } from '../../domain/usecases/PrepareSampleItemUseCase'
import { SearchSupportProgramsUseCase } from '../../domain/usecases/SearchSupportProgramsUseCase'
import type { AppContainer, AppCradle, AppServices } from './types'

/** Domain UseCase와 UseCase가 필요로 하는 Repository 연결을 등록합니다. */
export function registerUseCases(container: AppContainer) {
  container.register({
    prepareSampleItem: asFunction(createPrepareSampleItem).singleton(),
    searchSupportPrograms: asFunction(createSearchSupportPrograms).singleton(),
  })
}

function createPrepareSampleItem({
  sampleItemRepository,
}: Pick<AppCradle, 'sampleItemRepository'>): AppServices['prepareSampleItem'] {
  return (command, signal) => prepareSampleItem(sampleItemRepository, command, signal)
}

function createSearchSupportPrograms({
  supportProgramRepository,
}: Pick<AppCradle, 'supportProgramRepository'>): AppServices['searchSupportPrograms'] {
  return new SearchSupportProgramsUseCase(supportProgramRepository)
}
