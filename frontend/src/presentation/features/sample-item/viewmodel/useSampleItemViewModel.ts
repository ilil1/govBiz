import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'

import { usePrepareSampleItemMutation } from '../../../../app/applicationApi'
import type { SampleItem } from '../../../../domain/entities/SampleItem'
import { sampleItemFormSchema, type SampleItemFormValues } from '../validation/sampleItemFormSchema'

export function useSampleItemViewModel() {
  const form = useForm<SampleItemFormValues>({
    resolver: zodResolver(sampleItemFormSchema),
    mode: 'onChange',
    defaultValues: {
      name: '',
      category: '',
      note: '',
    },
  })
  const [prepareSampleItem, preparationMutation] = usePrepareSampleItemMutation()

  function registerField<FieldName extends keyof SampleItemFormValues>(name: FieldName) {
    return form.register(name, {
      onChange: () => preparationMutation.reset(),
    })
  }

  const prepare = form.handleSubmit((values) => {
    const item: SampleItem = {
      name: values.name,
      category: values.category || null,
      note: toNullableText(values.note),
    }
    void prepareSampleItem({ item })
  })

  return {
    errors: form.formState.errors,
    isReady: form.formState.isValid && !preparationMutation.isLoading,
    isPreparing: preparationMutation.isLoading,
    preparation: preparationMutation.data ?? null,
    preparationError: preparationMutation.isError
      ? 'Core API에 예제 요청을 전달하지 못했습니다. 연결 상태와 입력을 다시 확인하세요.'
      : null,
    registerField,
    prepare,
  }
}

function toNullableText(value: string) {
  const normalized = value.trim()
  return normalized === '' ? null : normalized
}
