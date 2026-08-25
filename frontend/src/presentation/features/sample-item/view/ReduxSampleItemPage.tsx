import type { FormEvent } from 'react'

import './SampleItemPage.css'

import { CoreApiConnectionStatus } from '../../../shared/core-api-status/CoreApiConnectionStatus'
import { useReduxSampleItemViewModel } from '../viewmodel/useReduxSampleItemViewModel'
import { SampleItemComparisonSummary, SampleItemVersionSwitch } from './SampleItemComparison'

type ReduxSampleItemPageProps = {
  onBackToChat: () => void
  onOpenHookVersion: () => void
}

export function ReduxSampleItemPage({
  onBackToChat,
  onOpenHookVersion,
}: ReduxSampleItemPageProps) {
  const {
    actionMessage,
    errors,
    isReady,
    preparation,
    preparationError,
    prepare,
    reset,
    submitButtonLabel,
    updateCategory,
    updateName,
    updateNote,
    values,
  } = useReduxSampleItemViewModel()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void prepare()
  }

  return (
    <main className="sample-shell">
      <button className="sample-back-button" type="button" onClick={onBackToChat}>
        <span aria-hidden="true">←</span> 지원사업 채팅으로 돌아가기
      </button>

      <SampleItemVersionSwitch
        activeVersion="redux"
        onOpenHookVersion={onOpenHookVersion}
        onOpenReduxVersion={() => undefined}
      />

      <section className="sample-hero" aria-label="GovBiz Redux 구조 예제 소개">
        <div>
          <p className="eyebrow">Redux Toolkit Architecture Example</p>
          <h1>Redux 기반 수직 슬라이스</h1>
          <p>
            폼 값과 요청 결과를 Redux Store에 저장하고, ViewModel이 같은 UseCase·Repository·HTTP
            계약을 실행합니다. 화면을 나갔다 돌아와도 완료 상태가 유지됩니다.
          </p>
        </div>
        <CoreApiConnectionStatus />
      </section>

      <SampleItemComparisonSummary activeVersion="redux" />

      <form className="sample-form" onSubmit={handleSubmit}>
        <div className="form-heading sample-form-heading-with-action">
          <div>
            <p className="eyebrow">Redux sample feature</p>
            <h2>Sample item 준비</h2>
            <p>입력·요청 상태·결과는 <code>state.sampleItemRedux</code>가 소유합니다.</p>
          </div>
          <button className="sample-reset-button" type="button" onClick={reset}>
            Redux 상태 초기화
          </button>
        </div>

        <label className="form-field" htmlFor="redux-sample-name">
          <span>이름</span>
          <input
            id="redux-sample-name"
            value={values.name}
            placeholder="예: Redux 예제"
            aria-invalid={Boolean(errors.name)}
            onChange={(event) => updateName(event.target.value)}
          />
          {errors.name ? <small role="alert">{errors.name}</small> : null}
        </label>

        <label className="form-field" htmlFor="redux-sample-category">
          <span>카테고리 <em>선택</em></span>
          <select
            id="redux-sample-category"
            value={values.category}
            onChange={(event) => updateCategory(event.target.value)}
          >
            <option value="">선택하지 않음</option>
            <option value="BASIC">Basic</option>
            <option value="EXTENDED">Extended</option>
          </select>
        </label>

        <label className="form-field" htmlFor="redux-sample-note">
          <span>메모 <em>선택</em></span>
          <textarea
            id="redux-sample-note"
            rows={4}
            value={values.note}
            placeholder="Redux Store에 유지할 메모를 적어 보세요."
            aria-invalid={Boolean(errors.note)}
            onChange={(event) => updateNote(event.target.value)}
          />
          {errors.note ? <small role="alert">{errors.note}</small> : null}
        </label>

        <div className="form-action">
          <div>
            <strong>{actionMessage}</strong>
            <p>AbortController는 직렬화할 수 없으므로 Redux가 아니라 ViewModel ref가 관리합니다.</p>
          </div>
          <button type="submit" disabled={!isReady}>
            {submitButtonLabel}
          </button>
        </div>

        {preparation ? (
          <output className="sample-result" aria-live="polite">
            <strong className="sample-result-title">✓ Redux Store에 요청 성공 저장</strong>
            <span>
              <strong>{preparation.item.name}</strong> 입력을 서버가 정상적으로 받았습니다.
            </span>
            <small>
              phase: {preparation.phase} · processing.status: {preparation.processing.status}
              {' '}— 이 결과는 다른 화면으로 이동해도 Store에 남습니다.
            </small>
          </output>
        ) : null}

        {preparationError ? <p className="sample-error" role="alert">{preparationError}</p> : null}
      </form>
    </main>
  )
}
