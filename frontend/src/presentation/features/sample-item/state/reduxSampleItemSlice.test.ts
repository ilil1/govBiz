import { describe, expect, it } from 'vitest'

import { createAppStore } from '../../../../app/store'
import {
  nameChanged,
  noteChanged,
  preparationFailed,
  preparationStarted,
  preparationSucceeded,
  reduxSampleItemReset,
  selectIsReduxSampleItemReady,
  selectReduxSampleItemButtonLabel,
  selectReduxSampleItemErrors,
} from './reduxSampleItemSlice'

describe('reduxSampleItemSlice', () => {
  it('validates fields and derives whether the request is ready', () => {
    const store = createAppStore()

    expect(selectIsReduxSampleItemReady(store.getState())).toBe(false)
    store.dispatch(nameChanged('Redux 예제'))
    expect(selectIsReduxSampleItemReady(store.getState())).toBe(true)

    store.dispatch(noteChanged('a'.repeat(501)))
    expect(selectIsReduxSampleItemReady(store.getState())).toBe(false)
    expect(selectReduxSampleItemErrors(store.getState()).note).toBe(
      '메모는 500자 이하여야 합니다.',
    )
  })

  it('accepts only the active request result and keeps actions serializable', () => {
    const store = createAppStore()
    store.dispatch(nameChanged('Redux 예제'))
    const started = preparationStarted()
    const ignoredStart = preparationStarted()
    store.dispatch(started)
    store.dispatch(ignoredStart)

    expect(store.getState().sampleItemRedux.activeRequestId).toBe(started.payload.requestId)
    expect(selectIsReduxSampleItemReady(store.getState())).toBe(false)

    store.dispatch(preparationSucceeded({
      preparation: successfulPreparation('무시할 결과'),
      requestId: 'stale-request',
    }))
    expect(store.getState().sampleItemRedux.preparation).toBeNull()

    store.dispatch(preparationSucceeded({
      preparation: successfulPreparation('Redux 예제'),
      requestId: started.payload.requestId,
    }))
    expect(store.getState().sampleItemRedux.preparation?.item.name).toBe('Redux 예제')
    expect(selectReduxSampleItemButtonLabel(store.getState())).toBe('다시 확인')
  })

  it('derives retry state and supports an explicit reset', () => {
    const store = createAppStore()
    store.dispatch(nameChanged('재시도'))
    const first = preparationStarted()
    store.dispatch(first)
    store.dispatch(preparationFailed({ requestId: first.payload.requestId }))
    expect(selectReduxSampleItemButtonLabel(store.getState())).toBe('다시 요청')

    const retry = preparationStarted()
    store.dispatch(retry)
    expect(selectReduxSampleItemButtonLabel(store.getState())).toBe('다시 요청 중…')

    store.dispatch(reduxSampleItemReset())
    expect(store.getState().sampleItemRedux.values.name).toBe('')
    expect(store.getState().sampleItemRedux.status).toBe('idle')
  })
})

function successfulPreparation(name: string) {
  return {
    item: { category: null, name, note: null },
    phase: 'READY_FOR_PROCESSING' as const,
    processing: { status: 'NOT_STARTED' as const },
  }
}
