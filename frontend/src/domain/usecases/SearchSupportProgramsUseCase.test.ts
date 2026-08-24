import { describe, expect, it, vi } from 'vitest'

import { FixtureSupportProgramRepository } from '../../data/repositories/FixtureSupportProgramRepository'
import { SearchSupportProgramsUseCase } from './SearchSupportProgramsUseCase'

describe('SearchSupportProgramsUseCase', () => {
  const useCase = new SearchSupportProgramsUseCase(new FixtureSupportProgramRepository())

  it('finds open Seoul AI programs first', async () => {
    const result = await useCase.execute('서울 AI')

    expect(result.programs[0]?.id).toBe('fixture-seoul-ai-business')
    expect(result.programs.every((program) => program.status === 'OPEN')).toBe(true)
  })

  it('finds national export programs', async () => {
    const result = await useCase.execute('수출')

    expect(result.programs.map((program) => program.id)).toContain('fixture-export-voucher')
  })

  it('returns open programs for an empty query', async () => {
    const result = await useCase.execute('')

    expect(result.programs).toHaveLength(5)
  })

  it('normalizes the query and forwards request cancellation', async () => {
    const search = vi.fn().mockResolvedValue([])
    const controller = new AbortController()
    const cancellableUseCase = new SearchSupportProgramsUseCase({ search })

    await cancellableUseCase.execute('  서울 AI  ', controller.signal)

    expect(search).toHaveBeenCalledWith(
      { acceptingOnly: true, query: '서울 AI' },
      controller.signal,
    )
  })
})
