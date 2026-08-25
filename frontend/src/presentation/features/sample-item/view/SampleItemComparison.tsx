type SampleItemVersion = 'hook' | 'redux'

type SampleItemVersionSwitchProps = {
  activeVersion: SampleItemVersion
  onOpenHookVersion: () => void
  onOpenReduxVersion: () => void
}

export function SampleItemVersionSwitch({
  activeVersion,
  onOpenHookVersion,
  onOpenReduxVersion,
}: SampleItemVersionSwitchProps) {
  return (
    <nav className="sample-version-switch" aria-label="SampleItem 상태 관리 방식">
      <button
        type="button"
        aria-current={activeVersion === 'hook' ? 'page' : undefined}
        onClick={onOpenHookVersion}
      >
        React Hook 버전
      </button>
      <button
        type="button"
        aria-current={activeVersion === 'redux' ? 'page' : undefined}
        onClick={onOpenReduxVersion}
      >
        Redux Toolkit 버전
      </button>
    </nav>
  )
}

export function SampleItemComparisonSummary({ activeVersion }: { activeVersion: SampleItemVersion }) {
  return (
    <section className="sample-comparison" aria-labelledby={`${activeVersion}-comparison-title`}>
      <div>
        <p className="eyebrow">같은 기능, 다른 상태 관리</p>
        <h2 id={`${activeVersion}-comparison-title`}>두 버전에서 직접 확인할 차이</h2>
      </div>
      <div className="sample-comparison-table" role="table" aria-label="React Hook과 Redux 비교">
        <div role="row" className="comparison-header">
          <strong role="columnheader">항목</strong>
          <strong role="columnheader" data-active={activeVersion === 'hook'}>React Hook</strong>
          <strong role="columnheader" data-active={activeVersion === 'redux'}>Redux Toolkit</strong>
        </div>
        <ComparisonRow label="상태 위치" hook="화면 Hook" redux="전역 Store slice" activeVersion={activeVersion} />
        <ComparisonRow label="화면 이동" hook="입력·결과 초기화" redux="입력·결과 유지" activeVersion={activeVersion} />
        <ComparisonRow label="새로고침" hook="초기화" redux="초기화" activeVersion={activeVersion} />
        <ComparisonRow label="API·UseCase" hook="동일" redux="동일" activeVersion={activeVersion} />
      </div>
    </section>
  )
}

function ComparisonRow({
  activeVersion,
  hook,
  label,
  redux,
}: {
  activeVersion: SampleItemVersion
  hook: string
  label: string
  redux: string
}) {
  return (
    <div role="row">
      <span role="rowheader">{label}</span>
      <span role="cell" data-active={activeVersion === 'hook'}>{hook}</span>
      <span role="cell" data-active={activeVersion === 'redux'}>{redux}</span>
    </div>
  )
}
