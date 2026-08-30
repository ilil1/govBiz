import { configureStore } from '@reduxjs/toolkit'

import chatReducer from '../presentation/features/chat/state/chatSlice'

export function createAppStore() {
  return configureStore({
    reducer: {
      chat: chatReducer,
    },
  })
}

export type AppStore = ReturnType<typeof createAppStore>
export type RootState = ReturnType<AppStore['getState']>
export type AppDispatch = AppStore['dispatch']
