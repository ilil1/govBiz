import './CoreApiConnectionStatus.css'

import { useCoreApiHealth } from './useCoreApiHealth'

export function CoreApiConnectionStatus() {
  const { data, isError, isLoading, refetch } = useCoreApiHealth()

  if (isLoading) {
    return (
      <div className="core-api-status is-checking">
        <span className="connection-dot" />
        <div>
          <strong>Core API 연결 확인 중</strong>
          <p>Spring Boot 상태를 확인하고 있습니다.</p>
        </div>
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="core-api-status is-error">
        <span className="connection-dot" />
        <div>
          <strong>Core API에 연결할 수 없습니다</strong>
          <p>Spring Boot 실행 주소와 CORS 설정을 확인하세요.</p>
          <button type="button" className="connection-retry" onClick={() => void refetch()}>
            다시 확인
          </button>
        </div>
      </div>
    )
  }

  const isHealthy = data.status === 'up'

  return (
    <div className={`core-api-status ${isHealthy ? 'is-connected' : 'is-error'}`}>
      <span className="connection-dot" />
      <div>
        <strong>{isHealthy ? 'Core API 연결됨' : 'Core API 상태 확인 필요'}</strong>
        <p>
          {data.service} · 상태: {data.status}
        </p>
      </div>
    </div>
  )
}
