import type { AwilixContainer } from 'awilix/browser'

import type { CoreApiHealth } from '../../data/core-api/coreApiHealth'
import type { SampleItem } from '../../domain/entities/SampleItem'
import type { SampleItemPreparation } from '../../domain/entities/SampleItemPreparation'
import type { SampleItemRepository } from '../../domain/repositories/SampleItemRepository'
import type { SupportProgramRepository } from '../../domain/repositories/SupportProgramRepository'
import type { SearchSupportProgramsUseCase } from '../../domain/usecases/SearchSupportProgramsUseCase'

export type AppServices = {
  fetchCoreApiHealth(signal?: AbortSignal): Promise<CoreApiHealth>
  prepareSampleItem(item: SampleItem, signal?: AbortSignal): Promise<SampleItemPreparation>
  searchSupportPrograms: Pick<SearchSupportProgramsUseCase, 'execute'>
}

/** Awilix가 생성·연결할 수 있는 전체 의존성 목록입니다. */
export type AppCradle = {
  appServices: AppServices
  fetchCoreApiHealth: AppServices['fetchCoreApiHealth']
  prepareSampleItem: AppServices['prepareSampleItem']
  sampleItemRepository: SampleItemRepository
  searchSupportPrograms: AppServices['searchSupportPrograms']
  supportProgramRepository: SupportProgramRepository
}

export type AppContainer = AwilixContainer<AppCradle>
