import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'

import { useAppDispatch } from '../../../../app/hooks'
import type { AppThunk } from '../../../../app/store'
import type { SampleItem } from '../../../../domain/entities/SampleItem'
import type { SampleItemPreparation } from '../../../../domain/entities/SampleItemPreparation'
import { sampleItemFormSchema, type SampleItemFormValues } from '../validation/sampleItemFormSchema'

export function useSampleItemViewModel() {
  const dispatch = useAppDispatch()
  const form = useForm<SampleItemFormValues>({
    resolver: zodResolver(sampleItemFormSchema),
    mode: 'onChange',
    defaultValues: {
      name: '',
      category: '',
      note: '',
    },
  })
  const activeController = useRef<AbortController | null>(null)
  const activeRequestId = useRef<number | null>(null)
  const isMounted = useRef(true)
  const requestSequence = useRef(0)
  const [isPreparing, setIsPreparing] = useState(false)
  const [preparation, setPreparation] = useState<SampleItemPreparation | null>(null)
  const [preparationError, setPreparationError] = useState<string | null>(null)

  useEffect(() => {
    isMounted.current = true

    return () => {
      isMounted.current = false
      activeController.current?.abort()
      activeController.current = null
      activeRequestId.current = null
      requestSequence.current += 1
    }
  }, [])

  function resetPreparation() {
    activeController.current?.abort()
    activeController.current = null
    activeRequestId.current = null
    requestSequence.current += 1
    setIsPreparing(false)
    setPreparation(null)
    setPreparationError(null)
  }

  function registerField<FieldName extends keyof SampleItemFormValues>(name: FieldName) {
    return form.register(name, {
      onChange: resetPreparation,
    })
  }

  const prepare = form.handleSubmit(async (values) => {
    if (activeRequestId.current !== null) return

    const item: SampleItem = {
      name: values.name,
      category: values.category || null,
      note: toNullableText(values.note),
    }
    const requestId = requestSequence.current + 1
    const controller = new AbortController()
    requestSequence.current = requestId
    activeController.current = controller
    activeRequestId.current = requestId
    setIsPreparing(true)
    setPreparation(null)
    setPreparationError(null)

    const preparationWorkflow: AppThunk<Promise<SampleItemPreparation>> = (
      _thunkDispatch,
      _getState,
      appServices,
    ) => appServices.prepareSampleItem({ item }, controller.signal)

    try {
      const result = await dispatch(preparationWorkflow)
      if (!isMounted.current || activeRequestId.current !== requestId) return
      setPreparation(result)
    } catch {
      if (!isMounted.current || activeRequestId.current !== requestId) return
      setPreparationError(
        'Core API에 예제 요청을 전달하지 못했습니다. 연결 상태와 입력을 다시 확인하세요.',
      )
    } finally {
      if (isMounted.current && activeRequestId.current === requestId) {
        activeController.current = null
        activeRequestId.current = null
        setIsPreparing(false)
      }
    }
  })

  return {
    errors: form.formState.errors,
    isReady: form.formState.isValid && !isPreparing,
    isPreparing,
    preparation,
    preparationError,
    registerField,
    prepare,
  }
}

function toNullableText(value: string) {
  const normalized = value.trim()
  return normalized === '' ? null : normalized
}
