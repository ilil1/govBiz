import './SampleItemPage.css'

import { CoreApiConnectionStatus } from '../../../shared/core-api-status/CoreApiConnectionStatus'
import { useSampleItemViewModel } from '../viewmodel/useSampleItemViewModel'

type SampleItemPageProps = {
  onBackToChat: () => void
}

export function SampleItemPage({ onBackToChat }: SampleItemPageProps) {
  const {
    errors,
    isPreparing,
    isReady,
    preparation,
    preparationError,
    prepare,
    registerField,
  } = useSampleItemViewModel()

  return (
    <main className="sample-shell">
      <button className="sample-back-button" type="button" onClick={onBackToChat}>
        <span aria-hidden="true">←</span> 지원사업 채팅으로 돌아가기
      </button>

      <section className="sample-hero" aria-label="GovBiz 구조 예제 소개">
        <div>
          <p className="eyebrow">GovBiz Architecture Example</p>
          <h1>재사용 가능한 수직 슬라이스</h1>
          <p>
            이 예제는 React의 View·ViewModel·UseCase·Repository와 Spring Boot의
            Controller·Service·Domain을 실제 HTTP 계약으로 연결합니다.
          </p>
        </div>
        <CoreApiConnectionStatus />
      </section>

      <form className="sample-form" onSubmit={prepare}>
        <div className="form-heading">
          <p className="eyebrow">Sample feature</p>
          <h2>Sample item 준비</h2>
          <p>필수 이름과 선택 항목을 Core API로 보내고, 처리 전 준비 상태를 확인합니다.</p>
        </div>

        <label className="form-field" htmlFor="sample-name">
          <span>이름</span>
          <input
            id="sample-name"
            placeholder="예: 첫 번째 예제"
            aria-invalid={Boolean(errors.name)}
            {...registerField('name')}
          />
          {errors.name ? <small role="alert">{errors.name.message}</small> : null}
        </label>

        <label className="form-field" htmlFor="sample-category">
          <span>카테고리 <em>선택</em></span>
          <select id="sample-category" {...registerField('category')}>
            <option value="">선택하지 않음</option>
            <option value="BASIC">Basic</option>
            <option value="EXTENDED">Extended</option>
          </select>
        </label>

        <label className="form-field" htmlFor="sample-note">
          <span>메모 <em>선택</em></span>
          <textarea
            id="sample-note"
            rows={4}
            placeholder="예제 기능의 목적이나 다음 처리 단계를 적어 보세요."
            aria-invalid={Boolean(errors.note)}
            {...registerField('note')}
          />
          {errors.note ? <small role="alert">{errors.note.message}</small> : null}
        </label>

        <div className="form-action">
          <div>
            <strong>{isReady ? '준비 요청을 보낼 수 있습니다.' : '이름을 입력하면 준비됩니다.'}</strong>
            <p>성공해도 실제 비동기 처리는 시작하지 않습니다.</p>
          </div>
          <button type="submit" disabled={!isReady}>
            {isPreparing ? '요청 중…' : '준비 상태 확인'}
          </button>
        </div>

        {preparation ? (
          <output className="sample-result" aria-live="polite">
            <strong>{preparation.item.name}</strong> · {preparation.phase} · 처리 상태{' '}
            <strong>{preparation.processing.status}</strong>
          </output>
        ) : null}

        {preparationError ? <p className="sample-error" role="alert">{preparationError}</p> : null}
      </form>
    </main>
  )
}
