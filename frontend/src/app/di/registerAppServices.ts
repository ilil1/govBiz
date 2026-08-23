import { asFunction, asValue } from 'awilix/browser'

import { fetchCoreApiHealth } from '../../data/core-api/fetchCoreApiHealth'
import type { AppContainer, AppCradle, AppServices } from './types'

/** 외부 서비스와 Redux Thunk에 노출할 AppServices facade를 등록합니다. */
export function registerAppServices(container: AppContainer) {
  container.register({
    appServices: asFunction(createAppServicesFacade).singleton(),
    fetchCoreApiHealth: asValue(fetchCoreApiHealth),
  })
}

function createAppServicesFacade({
  fetchCoreApiHealth,
  prepareSampleItem,
  searchSupportPrograms,
}: Pick<
  AppCradle,
  'fetchCoreApiHealth' | 'prepareSampleItem' | 'searchSupportPrograms'
>): AppServices {
  return {
    fetchCoreApiHealth,
    prepareSampleItem,
    searchSupportPrograms,
  }
}
