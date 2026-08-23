import { z } from 'zod'

export const sampleItemFormSchema = z.object({
  name: z.string().trim().min(1, '이름을 입력하세요.').max(100, '이름은 100자 이하여야 합니다.'),
  category: z.union([z.literal(''), z.enum(['BASIC', 'EXTENDED'])]),
  note: z.string().max(500, '메모는 500자 이하여야 합니다.'),
})

export type SampleItemFormValues = z.infer<typeof sampleItemFormSchema>
