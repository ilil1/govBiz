import { supportPrograms } from '../fixtures/supportPrograms'
import type { SupportProgram } from '../../domain/entities/SupportProgram'
import type {
  SupportProgramRepository,
  SupportProgramSearch,
} from '../../domain/repositories/SupportProgramRepository'

const regionAliases: Record<string, string> = {
  서울: '서울',
  경기: '경기',
  경기도: '경기',
  부산: '부산',
  대전: '대전',
  전국: '전국',
}

const categoryAliases: Record<string, string> = {
  ai: 'AI',
  인공지능: 'AI',
  기술: '기술',
  창업: '창업',
  제조: '제조',
  수출: '수출',
  콘텐츠: '콘텐츠',
  해외진출: '해외진출',
  스마트공장: '스마트공장',
}

export class FixtureSupportProgramRepository implements SupportProgramRepository {
  async search({ query, acceptingOnly = true }: SupportProgramSearch): Promise<SupportProgram[]> {
    const normalizedQuery = query.trim().toLowerCase()
    const terms = normalizedQuery.split(/\s+/).filter(Boolean)

    return supportPrograms
      .filter((program) => !acceptingOnly || program.status === 'OPEN')
      .map((program) => ({ program, score: this.score(program, terms) }))
      .filter(({ score }) => score > 0 || terms.length === 0)
      .sort((left, right) => right.score - left.score)
      .slice(0, 5)
      .map(({ program }) => program)
  }

  private score(program: SupportProgram, terms: string[]): number {
    if (terms.length === 0) return 1

    return terms.reduce((score, term) => {
      const region = regionAliases[term]
      const category = categoryAliases[term]
      const searchable = [
        program.title,
        program.summary,
        program.targetDescription,
        ...program.categories,
        ...program.regions,
      ].join(' ').toLowerCase()

      if (region && (program.regions.includes(region) || program.regions.includes('전국'))) {
        return score + 5
      }
      if (category && program.categories.includes(category)) return score + 4
      if (searchable.includes(term)) return score + 1
      return score
    }, 0)
  }
}
