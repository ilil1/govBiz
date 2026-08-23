export type SupportProgramStatus = 'OPEN' | 'UPCOMING' | 'CLOSED'

export type SupportProgram = {
  id: string
  title: string
  organization: string
  summary: string
  categories: string[]
  regions: string[]
  targetDescription: string
  supportAmount: string
  applicationStartDate: string
  applicationEndDate: string
  status: SupportProgramStatus
  sourceName: string
  sourceUrl: string
  matchedReasons: string[]
}
