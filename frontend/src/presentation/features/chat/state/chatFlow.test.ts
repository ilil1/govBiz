import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { RootState } from '../../../../app/store'
import type { AppServices } from '../../../../app/services'
import { createAppStore } from '../../../../app/store'
import { supportPrograms } from '../../../../data/fixtures/supportPrograms'
import { conversationReset, draftChanged } from './chatSlice'
import { useSupportProgramChatViewModel } from '../viewmodel/useSupportProgramChatViewModel'

const hookMocks = vi.hoisted(() => ({
  useAppDispatch: vi.fn(),
  useAppSelector: vi.fn(),
}))

vi.mock('../../../../app/hooks', () => hookMocks)

describe('Redux chat flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('stores the user message and injected search service result in the chat slice', async () => {
    const execute = vi.fn().mockResolvedValue({
      query: '서울 AI',
      programs: [supportPrograms[0]],
    })
    const store = createAppStore(createTestServices(execute))

    store.dispatch(draftChanged('서울 AI'))
    await useTestViewModel(store).submitMessage()

    const chat = store.getState().chat
    expect(execute).toHaveBeenCalledOnce()
    expect(chat.searchStatus).toBe('idle')
    expect(chat.messages.map((message) => message.role)).toEqual([
      'assistant',
      'user',
      'assistant',
    ])
    expect(chat.messages.at(-1)?.programs?.[0]?.id).toBe('fixture-seoul-ai-business')
  })

  it('does not start a duplicate search while the first request is pending', async () => {
    const pending = deferredSearchResult()
    const execute = vi.fn().mockReturnValue(pending.promise)
    const store = createAppStore(createTestServices(execute))

    store.dispatch(draftChanged('수출'))
    const viewModel = useTestViewModel(store)
    const firstSearch = viewModel.submitMessage()
    const duplicateSearch = viewModel.submitMessage()
    pending.resolve({ query: '수출', programs: [supportPrograms[3]] })
    await Promise.all([firstSearch, duplicateSearch])

    expect(execute).toHaveBeenCalledOnce()
    expect(store.getState().chat.messages).toHaveLength(3)
  })

  it('ignores a result that arrives after the conversation was reset', async () => {
    const pending = deferredSearchResult()
    const store = createAppStore(createTestServices(vi.fn().mockReturnValue(pending.promise)))

    store.dispatch(draftChanged('제조'))
    const search = useTestViewModel(store).submitMessage()
    store.dispatch(conversationReset())
    pending.resolve({ query: '제조', programs: [supportPrograms[2]] })
    await search

    const chat = store.getState().chat
    expect(chat.searchStatus).toBe('idle')
    expect(chat.messages).toHaveLength(1)
  })

  it('ignores an error that arrives after the conversation was reset', async () => {
    const pending = deferredSearchResult()
    const store = createAppStore(createTestServices(vi.fn().mockReturnValue(pending.promise)))

    store.dispatch(draftChanged('제조'))
    const search = useTestViewModel(store).submitMessage()
    store.dispatch(conversationReset())
    pending.reject(new Error('late search failure'))
    await search

    const chat = store.getState().chat
    expect(chat.searchStatus).toBe('idle')
    expect(chat.searchError).toBeNull()
    expect(chat.messages).toHaveLength(1)
  })

  it('stores a safe error when the search service fails', async () => {
    const execute = vi.fn().mockRejectedValue(new Error('internal fixture error'))
    const store = createAppStore(createTestServices(execute))

    store.dispatch(draftChanged('서울'))
    await useTestViewModel(store).submitMessage()

    const chat = store.getState().chat
    expect(chat.searchStatus).toBe('failed')
    expect(chat.searchError).toBe('지원사업을 검색하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  })
})

function useTestViewModel(store: ReturnType<typeof createAppStore>) {
  hookMocks.useAppDispatch.mockReturnValue(store.dispatch)
  hookMocks.useAppSelector.mockImplementation(
    (selector: (state: RootState) => unknown) => selector(store.getState()),
  )
  return useSupportProgramChatViewModel()
}

function createTestServices(
  execute: AppServices['searchSupportPrograms']['execute'],
): AppServices {
  return {
    async fetchCoreApiHealth() {
      throw new Error('not used')
    },
    async prepareSampleItem() {
      throw new Error('not used')
    },
    searchSupportPrograms: { execute },
  }
}

function deferredSearchResult() {
  type Result = Awaited<ReturnType<AppServices['searchSupportPrograms']['execute']>>
  let resolve!: (result: Result) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<Result>((complete, fail) => {
    resolve = complete
    reject = fail
  })
  return { promise, reject, resolve }
}
