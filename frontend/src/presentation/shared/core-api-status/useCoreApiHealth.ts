import { useQuery } from '@tanstack/react-query'

import { fetchCoreApiHealth } from '../../../data/core-api/fetchCoreApiHealth'

const CORE_API_HEALTH_QUERY_KEY = ['core-api', 'health'] as const

/** React Query가 Core API 연결 결과, 로딩, 오류, 재시도를 서버 상태로 관리합니다. */
export function useCoreApiHealth() {
  return useQuery({
    queryKey: CORE_API_HEALTH_QUERY_KEY,
    queryFn: ({ signal }) => fetchCoreApiHealth(signal),
    staleTime: 30_000,
    retry: false,
    refetchOnWindowFocus: false,
  })
}
