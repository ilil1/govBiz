import { useAppDispatch, useAppSelector } from '../../../../app/hooks'
import type { AppThunk } from '../../../../app/store'
import {
  conversationReset,
  draftChanged,
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
  const conversationCount = useAppSelector(selectConversationCount)
  const draft = useAppSelector(selectChatDraft)
  const isReadyToSubmit = useAppSelector(selectIsReadyToSubmit)
  const isSearching = useAppSelector(selectIsChatSearching)
  const messages = useAppSelector(selectChatMessages)
  const searchError = useAppSelector(selectChatSearchError)

  function startNewConversation() {
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
      thunkDispatch(startedAction)

      try {
        const result = await appServices.searchSupportPrograms.execute(query)
        thunkDispatch(searchSucceeded({
          programs: result.programs,
          requestId: startedAction.payload.requestId,
        }))
      } catch {
        thunkDispatch(searchFailed({ requestId: startedAction.payload.requestId }))
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
