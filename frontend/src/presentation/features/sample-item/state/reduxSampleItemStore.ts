import { configureStore } from '@reduxjs/toolkit'
import { useDispatch, useSelector } from 'react-redux'

import reduxSampleItemReducer from './reduxSampleItemSlice'

/** Redux SampleItem 화면만 사용하는 기능 전용 Store입니다. */
export function createSampleItemStore() {
  return configureStore({
    reducer: {
      sampleItemRedux: reduxSampleItemReducer,
    },
  })
}

export type SampleItemStore = ReturnType<typeof createSampleItemStore>
export type SampleItemRootState = ReturnType<SampleItemStore['getState']>
export type SampleItemDispatch = SampleItemStore['dispatch']

export const useSampleItemDispatch = useDispatch.withTypes<SampleItemDispatch>()
export const useSampleItemSelector = useSelector.withTypes<SampleItemRootState>()
