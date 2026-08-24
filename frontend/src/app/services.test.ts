import { asValue } from 'awilix/browser'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { supportPrograms } from '../data/fixtures/supportPrograms'
import type { SupportProgramRepository } from '../domain/repositories/SupportProgramRepository'
import { createAppContainer } from './di/container'

describe('Awilix application container', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('resolves the production graph and executes the Core API search', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      query: '서울 AI',
      programs: [supportPrograms[0]],
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    const container = createAppContainer()
    const result = await container
      .resolve('appServices')
      .searchSupportPrograms.execute('서울 AI')

    expect(result.query).toBe('서울 AI')
    expect(result.programs[0]?.id).toBe('fixture-seoul-ai-business')
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('reuses singletons inside one container and isolates fresh containers', () => {
    const first = createAppContainer()
    const second = createAppContainer()

    expect(first.resolve('appServices')).toBe(first.resolve('appServices'))
    expect(first.resolve('supportProgramRepository')).toBe(
      first.resolve('supportProgramRepository'),
    )
    expect(first.resolve('appServices')).not.toBe(second.resolve('appServices'))
    expect(first.resolve('searchSupportPrograms')).not.toBe(
      second.resolve('searchSupportPrograms'),
    )
  })

  it('injects a repository override into the real search use case', async () => {
    const search = vi.fn().mockResolvedValue([supportPrograms[3]])
    const repository: SupportProgramRepository = { search }
    const container = createAppContainer()
    container.register({ supportProgramRepository: asValue(repository) })

    const result = await container.resolve('appServices').searchSupportPrograms.execute('수출')

    expect(search).toHaveBeenCalledWith(
      { acceptingOnly: true, query: '수출' },
      undefined,
    )
    expect(result.programs).toEqual([supportPrograms[3]])
  })
})
