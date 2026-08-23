import { useAppDispatch, useAppSelector } from '../../../../app/hooks'
import {
  conversationReset,
  draftChanged,
  selectChatDraft,
  selectChatMessages,
  selectChatSearchError,
  selectConversationCount,
  selectIsChatSearching,
  selectIsReadyToSubmit,
} from '../state/chatSlice'
import { submitSupportProgramSearch } from '../state/chatThunks'

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
    void dispatch(submitSupportProgramSearch())
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
