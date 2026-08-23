import { configureStore, type ThunkAction, type UnknownAction } from '@reduxjs/toolkit'
import { setupListeners } from '@reduxjs/toolkit/query'

import chatReducer from '../presentation/features/chat/state/chatSlice'
import { applicationApi } from './applicationApi'
import { createAppServices, type AppServices } from './services'

export function createAppStore(appServices: AppServices = createAppServices()) {
  return configureStore({
    reducer: {
      chat: chatReducer,
      [applicationApi.reducerPath]: applicationApi.reducer,
    },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware({
        thunk: {
          extraArgument: appServices,
        },
      }).concat(applicationApi.middleware),
  })
}

export const store = createAppStore()
setupListeners(store.dispatch)

export type AppStore = ReturnType<typeof createAppStore>
export type RootState = ReturnType<AppStore['getState']>
export type AppDispatch = AppStore['dispatch']
export type AppThunk<ReturnType = void> = ThunkAction<
  ReturnType,
  RootState,
  AppServices,
  UnknownAction
>
