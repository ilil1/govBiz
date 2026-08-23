import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'

import { SampleItemRepositoryImpl } from '../../../../data/repositories/SampleItemRepositoryImpl'
import type { SampleItem } from '../../../../domain/entities/SampleItem'
import { prepareSampleItem } from '../../../../domain/usecases/PrepareSampleItemUseCase'
import { sampleItemFormSchema, type SampleItemFormValues } from '../validation/sampleItemFormSchema'

const sampleItemRepository = new SampleItemRepositoryImpl()

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
  const preparationMutation = useMutation({
    mutationFn: prepareSampleItem.bind(null, sampleItemRepository),
  })

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
    preparationMutation.mutate({ item })
  })

  return {
    errors: form.formState.errors,
    isReady: form.formState.isValid && !preparationMutation.isPending,
    isPreparing: preparationMutation.isPending,
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
