// @vitest-environment jsdom

import type { FormEvent, ReactNode } from 'react'
import { Provider } from 'react-redux'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import type { AppServices } from '../../../../app/services'
import { createAppStore } from '../../../../app/store'
import type { SampleItem } from '../../../../domain/entities/SampleItem'
import type { SampleItemPreparation } from '../../../../domain/entities/SampleItemPreparation'
import { useSampleItemViewModel } from './useSampleItemViewModel'

afterEach(cleanup)

describe('useSampleItemViewModel', () => {
  it('normalizes the item, blocks duplicate submits, and stores the result', async () => {
    const pending = deferred<SampleItemPreparation>()
    const prepareSampleItem = vi.fn(
      (_item: SampleItem, _signal?: AbortSignal) => pending.promise,
    )
    const store = createAppStore(createTestServices({ prepareSampleItem }))
    render(<TestHarness />, { wrapper: createWrapper(store) })

    fireEvent.change(screen.getByLabelText('이름'), { target: { value: '예제' } })
    fireEvent.change(screen.getByLabelText('메모'), { target: { value: '  설명  ' } })
    await waitFor(() => expect(submitButton().disabled).toBe(false))

    fireEvent.submit(screen.getByRole('form'))
    fireEvent.submit(screen.getByRole('form'))
    await waitFor(() => expect(prepareSampleItem).toHaveBeenCalledOnce())
    expect(prepareSampleItem.mock.calls[0][0]).toEqual({
      category: null,
      name: '예제',
      note: '설명',
    })
    expect(prepareSampleItem.mock.calls[0][1]).toBeInstanceOf(AbortSignal)

    await act(async () => {
      pending.resolve(successfulPreparation('예제'))
      await pending.promise
    })
    expect(screen.getByTestId('preparation').textContent).toBe('예제')
    expect(screen.getByTestId('status').textContent).toBe('idle')
  })

  it('aborts and ignores a pending response when the input changes', async () => {
    const pending = deferred<SampleItemPreparation>()
    let requestSignal: AbortSignal | undefined
    const prepareSampleItem = vi.fn((_item: SampleItem, signal?: AbortSignal) => {
      requestSignal = signal
      return pending.promise
    })
    const store = createAppStore(createTestServices({ prepareSampleItem }))
    render(<TestHarness />, { wrapper: createWrapper(store) })

    fireEvent.change(screen.getByLabelText('이름'), { target: { value: '이전 값' } })
    await waitFor(() => expect(submitButton().disabled).toBe(false))
    fireEvent.submit(screen.getByRole('form'))
    await waitFor(() => expect(prepareSampleItem).toHaveBeenCalledOnce())

    fireEvent.change(screen.getByLabelText('이름'), { target: { value: '새 값' } })
    expect(requestSignal?.aborted).toBe(true)
    expect(screen.getByTestId('status').textContent).toBe('idle')

    await act(async () => {
      pending.resolve(successfulPreparation('이전 값'))
      await pending.promise
    })
    expect(screen.queryByTestId('preparation')).toBeNull()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('shows a safe error, resets it on input change, and aborts on unmount', async () => {
    const pending = deferred<SampleItemPreparation>()
    const prepareSampleItem = vi.fn()
      .mockRejectedValueOnce(new Error('private server detail'))
      .mockImplementationOnce((_item: SampleItem, _signal?: AbortSignal) => pending.promise)
    const store = createAppStore(createTestServices({ prepareSampleItem }))
    const { unmount } = render(<TestHarness />, { wrapper: createWrapper(store) })

    fireEvent.change(screen.getByLabelText('이름'), { target: { value: '오류 예제' } })
    await waitFor(() => expect(submitButton().disabled).toBe(false))
    fireEvent.submit(screen.getByRole('form'))
    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toContain('Core API에 예제 요청을 전달하지 못했습니다.')
    })
    expect(screen.getByRole('alert').textContent).not.toContain('private server detail')

    fireEvent.change(screen.getByLabelText('이름'), { target: { value: '재시도' } })
    expect(screen.queryByRole('alert')).toBeNull()
    await waitFor(() => expect(submitButton().disabled).toBe(false))
    fireEvent.submit(screen.getByRole('form'))
    await waitFor(() => expect(prepareSampleItem).toHaveBeenCalledTimes(2))
    const latestSignal = prepareSampleItem.mock.calls[1][1] as AbortSignal

    unmount()
    expect(latestSignal.aborted).toBe(true)
    pending.resolve(successfulPreparation('재시도'))
    await pending.promise
  })
})

function TestHarness() {
  const viewModel = useSampleItemViewModel()

  function submit(event: FormEvent<HTMLFormElement>) {
    void viewModel.prepare(event)
  }

  return (
    <form aria-label="sample form" onSubmit={submit}>
      <input aria-label="이름" {...viewModel.registerField('name')} />
      <textarea aria-label="메모" {...viewModel.registerField('note')} />
      <button type="submit" disabled={!viewModel.isReady}>요청</button>
      <span data-testid="status">{viewModel.isPreparing ? 'pending' : 'idle'}</span>
      {viewModel.preparation ? (
        <output data-testid="preparation">{viewModel.preparation.item.name}</output>
      ) : null}
      {viewModel.preparationError ? <p role="alert">{viewModel.preparationError}</p> : null}
    </form>
  )
}

function submitButton() {
  return screen.getByRole('button') as HTMLButtonElement
}

function createWrapper(store: ReturnType<typeof createAppStore>) {
  return function TestWrapper({ children }: { children: ReactNode }) {
    return <Provider store={store}>{children}</Provider>
  }
}

function createTestServices(overrides: Partial<AppServices>): AppServices {
  return {
    async fetchCoreApiHealth() {
      throw new Error('not used')
    },
    async prepareSampleItem() {
      throw new Error('not configured')
    },
    searchSupportPrograms: {
      async execute() {
        throw new Error('not used')
      },
    },
    ...overrides,
  }
}

function successfulPreparation(name: string): SampleItemPreparation {
  return {
    item: { category: null, name, note: null },
    phase: 'READY_FOR_PROCESSING',
    processing: { status: 'NOT_STARTED' },
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
