import type { SupportProgram } from '../entities/SupportProgram'

export type SupportProgramSearch = {
  query: string
  acceptingOnly?: boolean
}

/**
 * 채팅 화면은 이 포트만 사용합니다.
 * 지금은 fixture adapter를 사용하고, 실제 API 연결 시 같은 포트 뒤의 adapter만 교체합니다.
 */
export interface SupportProgramRepository {
  search(command: SupportProgramSearch): Promise<SupportProgram[]>
}
