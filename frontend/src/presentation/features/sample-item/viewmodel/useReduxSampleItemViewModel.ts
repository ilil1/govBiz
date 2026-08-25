import { useCallback, useEffect, useRef } from 'react'

import { appContainer } from '../../../../app/appContainer'
import { useAppDispatch, useAppSelector } from '../../../../app/hooks'
import type { AppDispatch, RootState } from '../../../../app/store'
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
import { sampleItemFormSchema, toSampleItem } from '../validation/sampleItemFormSchema'

type SampleItemUseCase = Pick<PrepareSampleItemUseCase, 'execute'>

export function useReduxSampleItemViewModel(
  prepareSampleItemUseCase: SampleItemUseCase = appContainer.resolve('prepareSampleItemUseCase'),
) {
  const dispatchToStore = useAppDispatch()
  const activeRequest = useRef<{
    controller: AbortController
    requestId: string
  } | null>(null)
  const actionMessage = useAppSelector(selectReduxSampleItemActionMessage)
  const errors = useAppSelector(selectReduxSampleItemErrors)
  const isPreparing = useAppSelector(selectIsReduxSampleItemPreparing)
  const isReady = useAppSelector(selectIsReduxSampleItemReady)
  const preparation = useAppSelector(selectReduxSampleItemPreparation)
  const preparationError = useAppSelector(selectReduxSampleItemError)
  const submitButtonLabel = useAppSelector(selectReduxSampleItemButtonLabel)
  const values = useAppSelector(selectReduxSampleItemValues)

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
      dispatchAction: AppDispatch,
      readCurrentState: () => RootState,
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
