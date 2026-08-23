import { createAppContainer } from './di/container'
import type { AppServices } from './di/types'

export type { AppServices } from './di/types'

/** Awilix가 조립한 기능 facade를 Redux Store에 전달합니다. */
export function createAppServices(): AppServices {
  return createAppContainer().resolve('appServices')
}
