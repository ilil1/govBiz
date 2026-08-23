// @vitest-environment jsdom

import type { ReactNode } from 'react'
import { Provider } from 'react-redux'
import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import type { AppServices } from '../../../app/services'
import { createAppStore } from '../../../app/store'
import type { CoreApiHealth } from '../../../data/core-api/coreApiHealth'
import { useCoreApiHealth } from './useCoreApiHealth'

afterEach(cleanup)

describe('useCoreApiHealth', () => {
  it('aborts the first StrictMode request and only applies the latest response', async () => {
    const first = deferred<CoreApiHealth>()
    const second = deferred<CoreApiHealth>()
    const signals: AbortSignal[] = []
    const fetchCoreApiHealth = vi.fn((signal?: AbortSignal) => {
      signals.push(signal!)
      return signals.length === 1 ? first.promise : second.promise
    })
    const store = createAppStore(createTestServices({ fetchCoreApiHealth }))
    const { result } = renderHook(useCoreApiHealth, {
      reactStrictMode: true,
      wrapper: createWrapper(store),
    })

    await waitFor(() => expect(fetchCoreApiHealth).toHaveBeenCalledTimes(2))
    expect(signals[0].aborted).toBe(true)

    const latestHealth = { service: 'govbiz-core-api', status: 'up' }
    await act(async () => {
      second.resolve(latestHealth)
      await second.promise
    })
    await waitFor(() => expect(result.current.data).toEqual(latestHealth))

    await act(async () => {
      first.resolve({ service: 'stale-core-api', status: 'down' })
      await first.promise
    })
    expect(result.current.data).toEqual(latestHealth)
    expect(result.current.isError).toBe(false)
  })

  it('aborts the previous request on refetch and aborts the latest request on unmount', async () => {
    const first = deferred<CoreApiHealth>()
    const second = deferred<CoreApiHealth>()
    const signals: AbortSignal[] = []
    const fetchCoreApiHealth = vi.fn((signal?: AbortSignal) => {
      signals.push(signal!)
      return signals.length === 1 ? first.promise : second.promise
    })
    const store = createAppStore(createTestServices({ fetchCoreApiHealth }))

    const { result, unmount } = renderHook(useCoreApiHealth, {
      wrapper: createWrapper(store),
    })
    await waitFor(() => expect(fetchCoreApiHealth).toHaveBeenCalledOnce())

    let refetchPromise!: Promise<void>
    act(() => {
      refetchPromise = result.current.refetch()
    })
    await waitFor(() => expect(fetchCoreApiHealth).toHaveBeenCalledTimes(2))
    expect(signals[0].aborted).toBe(true)

    const health = { service: 'govbiz-core-api', status: 'up' }
    await act(async () => {
      second.resolve(health)
      await refetchPromise
    })
    expect(result.current.data).toEqual(health)

    unmount()
    expect(signals[1].aborted).toBe(true)

    first.resolve({ service: 'stale-core-api', status: 'down' })
    await first.promise
  })

  it('shows a safe error state and recovers when retry succeeds', async () => {
    const fetchCoreApiHealth = vi.fn()
      .mockRejectedValueOnce(new Error('internal network detail'))
      .mockResolvedValueOnce({ service: 'govbiz-core-api', status: 'up' })
    const store = createAppStore(createTestServices({ fetchCoreApiHealth }))

    const { result } = renderHook(useCoreApiHealth, {
      wrapper: createWrapper(store),
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.data).toBeUndefined()

    await act(async () => {
      await result.current.refetch()
    })
    expect(result.current).toMatchObject({
      data: { service: 'govbiz-core-api', status: 'up' },
      isError: false,
      isLoading: false,
    })
  })
})

function createWrapper(store: ReturnType<typeof createAppStore>) {
  return function TestWrapper({ children }: { children: ReactNode }) {
    return <Provider store={store}>{children}</Provider>
  }
}

function createTestServices(overrides: Partial<AppServices>): AppServices {
  return {
    async fetchCoreApiHealth() {
      throw new Error('not configured')
    },
    async prepareSampleItem() {
      throw new Error('not used')
    },
    searchSupportPrograms: {
      async execute() {
        throw new Error('not used')
      },
    },
    ...overrides,
  }
}

function deferred<Result>() {
  let resolve!: (result: Result) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<Result>((complete, fail) => {
    resolve = complete
    reject = fail
  })
  return { promise, reject, resolve }
}
