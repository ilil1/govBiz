import { useGetCoreApiHealthQuery } from '../../../app/applicationApi'

/** RTK Query가 Core API 연결 결과와 서버 상태 lifecycle을 관리합니다. */
export function useCoreApiHealth() {
  return useGetCoreApiHealthQuery()
}
