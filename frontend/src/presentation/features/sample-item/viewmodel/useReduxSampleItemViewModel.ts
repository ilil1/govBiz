import { useCallback, useEffect, useRef } from 'react'

import { appContainer } from '../../../../app/appContainer'
import type { PrepareSampleItemUseCase } from '../../../../domain/usecases/PrepareSampleItemUseCase'
import {
  categoryChanged,
  nameChanged,
  noteChanged,
  preparationCancelled,
  preparationFailed,
  preparationStarted,
  preparationSucceeded,
  reduxSampleItemReset,
  selectIsReduxSampleItemPreparing,
  selectIsReduxSampleItemReady,
  selectReduxSampleItemActionMessage,
  selectReduxSampleItemButtonLabel,
  selectReduxSampleItemError,
  selectReduxSampleItemErrors,
  selectReduxSampleItemPreparation,
  selectReduxSampleItemState,
  selectReduxSampleItemValues,
} from '../state/reduxSampleItemSlice'
import {
  type SampleItemDispatch,
  type SampleItemRootState,
  useSampleItemDispatch,
  useSampleItemSelector,
} from '../state/reduxSampleItemStore'
import { sampleItemFormSchema, toSampleItem } from '../validation/sampleItemFormSchema'

type SampleItemUseCase = Pick<PrepareSampleItemUseCase, 'execute'>

export function useReduxSampleItemViewModel(
  prepareSampleItemUseCase: SampleItemUseCase = appContainer.resolve('prepareSampleItemUseCase'),
) {
  const dispatchToStore = useSampleItemDispatch()
  const activeRequest = useRef<{
    controller: AbortController
    requestId: string
  } | null>(null)
  const actionMessage = useSampleItemSelector(selectReduxSampleItemActionMessage)
  const errors = useSampleItemSelector(selectReduxSampleItemErrors)
  const isPreparing = useSampleItemSelector(selectIsReduxSampleItemPreparing)
  const isReady = useSampleItemSelector(selectIsReduxSampleItemReady)
  const preparation = useSampleItemSelector(selectReduxSampleItemPreparation)
  const preparationError = useSampleItemSelector(selectReduxSampleItemError)
  const submitButtonLabel = useSampleItemSelector(selectReduxSampleItemButtonLabel)
  const values = useSampleItemSelector(selectReduxSampleItemValues)

  const cancelActiveRequest = useCallback(() => {
    const request = activeRequest.current
    activeRequest.current = null
    if (!request) return

    request.controller.abort()
    dispatchToStore(preparationCancelled({ requestId: request.requestId }))
  }, [dispatchToStore])

  useEffect(() => () => {
    cancelActiveRequest()
  }, [cancelActiveRequest])

  function updateCategory(value: string) {
    if (value !== '' && value !== 'BASIC' && value !== 'EXTENDED') return
    cancelActiveRequest()
    dispatchToStore(categoryChanged(value))
  }

  function updateName(value: string) {
    cancelActiveRequest()
    dispatchToStore(nameChanged(value))
  }

  function updateNote(value: string) {
    cancelActiveRequest()
    dispatchToStore(noteChanged(value))
  }

  function prepare() {
    async function runPreparation(
      dispatchAction: SampleItemDispatch,
      readCurrentState: () => SampleItemRootState,
    ): Promise<void> {
      const currentState = selectReduxSampleItemState(readCurrentState())
      if (currentState.status === 'pending') return

      const validation = sampleItemFormSchema.safeParse(currentState.values)
      if (!validation.success) return

      const startedAction = preparationStarted()
      const controller = new AbortController()
      const requestId = startedAction.payload.requestId

      dispatchAction(startedAction)
      activeRequest.current = { controller, requestId }

      try {
        const result = await prepareSampleItemUseCase.execute(
          toSampleItem(validation.data),
          controller.signal,
        )
        if (controller.signal.aborted) return

        dispatchAction(preparationSucceeded({ preparation: result, requestId }))
      } catch {
        if (controller.signal.aborted) return
        dispatchAction(preparationFailed({ requestId }))
      } finally {
        if (activeRequest.current?.requestId === requestId) {
          activeRequest.current = null
        }
      }
    }

    return dispatchToStore(runPreparation)
  }

  function reset() {
    cancelActiveRequest()
    dispatchToStore(reduxSampleItemReset())
  }

  return {
    actionMessage,
    errors,
    isPreparing,
    isReady,
    preparation,
    preparationError,
    prepare,
    reset,
    submitButtonLabel,
    updateCategory,
    updateName,
    updateNote,
    values,
  }
}
