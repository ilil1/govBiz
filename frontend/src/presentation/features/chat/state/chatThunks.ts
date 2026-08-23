import { applicationApi } from '../../../../app/applicationApi'
import type { AppThunk } from '../../../../app/store'
import {
  searchFailed,
  searchStarted,
  searchSucceeded,
  selectChatState,
} from './chatSlice'

export function submitSupportProgramSearch(): AppThunk<Promise<void>> {
  return async (dispatch, getState) => {
    const chat = selectChatState(getState())
    const query = chat.draft.trim()
    if (!query || chat.searchStatus === 'pending') return

    const startedAction = searchStarted(query)
    dispatch(startedAction)

    try {
      const result = await dispatch(
        applicationApi.endpoints.searchSupportPrograms.initiate(query, {
          subscribe: false,
        }),
      ).unwrap()
      dispatch(searchSucceeded({
        programs: result.programs,
        requestId: startedAction.payload.requestId,
      }))
    } catch {
      dispatch(searchFailed({ requestId: startedAction.payload.requestId }))
    }
  }
}
