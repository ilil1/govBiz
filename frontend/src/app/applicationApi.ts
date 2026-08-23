import { createApi, fakeBaseQuery } from '@reduxjs/toolkit/query/react'

import type { CoreApiHealth } from '../data/core-api/coreApiHealth'
import type { SampleItemPreparation } from '../domain/entities/SampleItemPreparation'
import type { PrepareSampleItemCommand } from '../domain/repositories/SampleItemRepository'
import type { SearchSupportProgramsResult } from '../domain/usecases/SearchSupportProgramsUseCase'
import type { AppServices } from './services'

export type ApplicationApiError = {
  message: string
}

export const applicationApi = createApi({
  reducerPath: 'applicationApi',
  baseQuery: fakeBaseQuery<ApplicationApiError>(),
  endpoints: (builder) => ({
    getCoreApiHealth: builder.query<CoreApiHealth, void>({
      async queryFn(_argument, api) {
        return callService(() => getAppServices(api.extra).fetchCoreApiHealth(api.signal))
      },
      keepUnusedDataFor: 30,
    }),
    prepareSampleItem: builder.mutation<SampleItemPreparation, PrepareSampleItemCommand>({
      async queryFn(command, api) {
        return callService(() => getAppServices(api.extra).prepareSampleItem(command))
      },
    }),
    searchSupportPrograms: builder.query<SearchSupportProgramsResult, string>({
      async queryFn(query, api) {
        return callService(() => getAppServices(api.extra).searchSupportPrograms.execute(query))
      },
      keepUnusedDataFor: 300,
    }),
  }),
})

export const {
  useGetCoreApiHealthQuery,
  usePrepareSampleItemMutation,
} = applicationApi

function getAppServices(extra: unknown): AppServices {
  return extra as AppServices
}

async function callService<Result>(
  operation: () => Promise<Result>,
): Promise<{ data: Result } | { error: ApplicationApiError }> {
  try {
    return { data: await operation() }
  } catch {
    return {
      error: {
        message: '요청을 처리하지 못했습니다.',
      },
    }
  }
}
