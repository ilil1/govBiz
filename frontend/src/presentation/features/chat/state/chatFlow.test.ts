import { describe, expect, it, vi } from 'vitest'

import type { AppServices } from '../../../../app/services'
import { createAppStore } from '../../../../app/store'
import { supportPrograms } from '../../../../data/fixtures/supportPrograms'
import { conversationReset, draftChanged } from './chatSlice'
import { submitSupportProgramSearch } from './chatThunks'

describe('Redux chat flow', () => {
  it('stores the user message and RTK Query result in the chat slice', async () => {
    const execute = vi.fn().mockResolvedValue({
      query: '서울 AI',
      programs: [supportPrograms[0]],
    })
    const store = createAppStore(createTestServices(execute))

    store.dispatch(draftChanged('서울 AI'))
    await store.dispatch(submitSupportProgramSearch())

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
    const firstSearch = store.dispatch(submitSupportProgramSearch())
    const duplicateSearch = store.dispatch(submitSupportProgramSearch())
    pending.resolve({ query: '수출', programs: [supportPrograms[3]] })
    await Promise.all([firstSearch, duplicateSearch])

    expect(execute).toHaveBeenCalledOnce()
    expect(store.getState().chat.messages).toHaveLength(3)
  })

  it('ignores a result that arrives after the conversation was reset', async () => {
    const pending = deferredSearchResult()
    const store = createAppStore(createTestServices(vi.fn().mockReturnValue(pending.promise)))

    store.dispatch(draftChanged('제조'))
    const search = store.dispatch(submitSupportProgramSearch())
    store.dispatch(conversationReset())
    pending.resolve({ query: '제조', programs: [supportPrograms[2]] })
    await search

    const chat = store.getState().chat
    expect(chat.searchStatus).toBe('idle')
    expect(chat.messages).toHaveLength(1)
  })

  it('stores a safe error when the search service fails', async () => {
    const execute = vi.fn().mockRejectedValue(new Error('internal fixture error'))
    const store = createAppStore(createTestServices(execute))

    store.dispatch(draftChanged('서울'))
    await store.dispatch(submitSupportProgramSearch())

    const chat = store.getState().chat
    expect(chat.searchStatus).toBe('failed')
    expect(chat.searchError).toBe('지원사업을 검색하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  })
})

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
  const promise = new Promise<Result>((complete) => {
    resolve = complete
  })
  return { promise, resolve }
}
