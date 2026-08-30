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
  sampleItemReset,
  selectIsSampleItemPreparing,
  selectIsSampleItemReady,
  selectSampleItemActionMessage,
  selectSampleItemButtonLabel,
  selectSampleItemError,
  selectSampleItemErrors,
  selectSampleItemPreparation,
  selectSampleItemState,
  selectSampleItemValues,
} from '../state/sampleItemSlice'
import {
  type SampleItemDispatch,
  type SampleItemRootState,
  useSampleItemDispatch,
  useSampleItemSelector,
} from '../state/sampleItemStore'
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
  const actionMessage = useSampleItemSelector(selectSampleItemActionMessage)
  const errors = useSampleItemSelector(selectSampleItemErrors)
  const isPreparing = useSampleItemSelector(selectIsSampleItemPreparing)
  const isReady = useSampleItemSelector(selectIsSampleItemReady)
  const preparation = useSampleItemSelector(selectSampleItemPreparation)
  const preparationError = useSampleItemSelector(selectSampleItemError)
  const submitButtonLabel = useSampleItemSelector(selectSampleItemButtonLabel)
  const values = useSampleItemSelector(selectSampleItemValues)

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
      const currentState = selectSampleItemState(readCurrentState())
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
    dispatchToStore(sampleItemReset())
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
