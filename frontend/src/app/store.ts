import { configureStore } from '@reduxjs/toolkit'

import chatReducer from '../presentation/features/chat/state/chatSlice'
import reduxSampleItemReducer from '../presentation/features/sample-item/state/reduxSampleItemSlice'

export function createAppStore() {
  return configureStore({
    reducer: {
      chat: chatReducer,
      sampleItemRedux: reduxSampleItemReducer,
    },
  })
}

export type AppStore = ReturnType<typeof createAppStore>
export type RootState = ReturnType<AppStore['getState']>
export type AppDispatch = AppStore['dispatch']
