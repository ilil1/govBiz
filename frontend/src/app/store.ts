import { configureStore, type ThunkAction, type UnknownAction } from '@reduxjs/toolkit'

import chatReducer from '../presentation/features/chat/state/chatSlice'
import { createAppServices, type AppServices } from './services'

export function createAppStore(appServices: AppServices = createAppServices()) {
  return configureStore({
    reducer: {
      chat: chatReducer,
    },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware({
        thunk: {
          extraArgument: appServices,
        },
      }),
  })
}

export const store = createAppStore()

export type AppStore = ReturnType<typeof createAppStore>
export type RootState = ReturnType<AppStore['getState']>
export type AppDispatch = AppStore['dispatch']
export type AppThunk<ReturnType = void> = ThunkAction<
  ReturnType,
  RootState,
  AppServices,
  UnknownAction
>
