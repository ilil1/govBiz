import { useEffect, useRef } from 'react'

import { useAppDispatch, useAppSelector } from '../../../../app/hooks'
import type { AppThunk } from '../../../../app/store'
import {
  conversationReset,
  draftChanged,
  searchCancelled,
  searchFailed,
  searchStarted,
  searchSucceeded,
  selectChatDraft,
  selectChatMessages,
  selectChatSearchError,
  selectChatState,
  selectConversationCount,
  selectIsChatSearching,
  selectIsReadyToSubmit,
} from '../state/chatSlice'

export const supportProgramChatSuggestions = [
  '서울 AI 창업지원 사업 찾아줘',
  '현재 접수 중인 수출 지원사업 알려줘',
  '제조기업 R&D 사업을 찾아줘',
]

export function useSupportProgramChatViewModel() {
  const dispatch = useAppDispatch()
  const activeRequest = useRef<{
    controller: AbortController
    requestId: string
  } | null>(null)
  const conversationCount = useAppSelector(selectConversationCount)
  const draft = useAppSelector(selectChatDraft)
  const isReadyToSubmit = useAppSelector(selectIsReadyToSubmit)
  const isSearching = useAppSelector(selectIsChatSearching)
  const messages = useAppSelector(selectChatMessages)
  const searchError = useAppSelector(selectChatSearchError)

  useEffect(() => () => {
    const request = activeRequest.current
    activeRequest.current = null
    if (!request) return

    request.controller.abort()
    dispatch(searchCancelled({ requestId: request.requestId }))
  }, [dispatch])

  function startNewConversation() {
    const request = activeRequest.current
    activeRequest.current = null
    request?.controller.abort()
    dispatch(conversationReset())
  }

  function selectSuggestion(suggestion: string) {
    dispatch(draftChanged(suggestion))
  }

  function updateDraft(value: string) {
    dispatch(draftChanged(value))
  }

  function submitMessage() {
    const searchWorkflow: AppThunk<Promise<void>> = async (
      thunkDispatch,
      getState,
      appServices,
    ) => {
      const chat = selectChatState(getState())
      const query = chat.draft.trim()
      if (!query || chat.searchStatus === 'pending') return

      const startedAction = searchStarted(query)
      const controller = new AbortController()
      thunkDispatch(startedAction)
      activeRequest.current = {
        controller,
        requestId: startedAction.payload.requestId,
      }

      try {
        const result = await appServices.searchSupportPrograms.execute(query, controller.signal)
        if (controller.signal.aborted) return
        thunkDispatch(searchSucceeded({
          programs: result.programs,
          requestId: startedAction.payload.requestId,
        }))
      } catch {
        if (controller.signal.aborted) return
        thunkDispatch(searchFailed({ requestId: startedAction.payload.requestId }))
      } finally {
        if (activeRequest.current?.requestId === startedAction.payload.requestId) {
          activeRequest.current = null
        }
      }
    }

    return dispatch(searchWorkflow)
  }

  return {
    conversationCount,
    draft,
    isReadyToSubmit,
    isSearching,
    messages,
    searchError,
    selectSuggestion,
    startNewConversation,
    submitMessage,
    updateDraft,
  }
}
