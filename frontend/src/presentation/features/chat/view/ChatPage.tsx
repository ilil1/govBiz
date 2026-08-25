import { type FormEvent, useEffect, useRef, useState } from 'react'

import type { SupportProgram } from '../../../../domain/entities/SupportProgram'
import {
  supportProgramChatSuggestions,
  useSupportProgramChatViewModel,
} from '../viewmodel/useSupportProgramChatViewModel'
import './ChatPage.css'

type ChatPageProps = {
  onOpenSampleItem: () => void
}

export function ChatPage({ onOpenSampleItem }: ChatPageProps) {
  const {
    conversationCount,
    draft,
    isReadyToSubmit,
    isSearching,
    messages,
    searchError,
    selectSuggestion,
    startNewConversation,
    submitMessage,
    updateDraft,
  } = useSupportProgramChatViewModel()
  const [isSidebarOpen, setIsSidebarOpen] = useState(false)
  const timelineRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const timeline = timelineRef.current
    if (timeline) timeline.scrollTop = timeline.scrollHeight
  }, [messages, isSearching])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void submitMessage()
  }

  function handleStartNewConversation() {
    startNewConversation()
    setIsSidebarOpen(false)
  }

  function handleSelectSuggestion(suggestion: string) {
    selectSuggestion(suggestion)
    setIsSidebarOpen(false)
  }

  return (
    <main className="chat-app">
      <button
        className="sidebar-backdrop"
        aria-label="메뉴 닫기"
        data-visible={isSidebarOpen}
        onClick={() => setIsSidebarOpen(false)}
      />

      <aside className={`chat-sidebar${isSidebarOpen ? ' is-open' : ''}`}>
        <div className="brand-lockup">
          <span className="brand-mark" aria-hidden="true">G</span>
          <div>
            <strong>GovBiz</strong>
            <span>지원사업 탐색 도우미</span>
          </div>
        </div>

        <div className="sidebar-actions">
          <button className="new-chat-button" type="button" onClick={handleStartNewConversation}>
            <span aria-hidden="true">＋</span> 새 대화 시작
          </button>
          <button className="sample-page-button" type="button" onClick={onOpenSampleItem}>
            <span aria-hidden="true">▦</span> 상태관리 비교 예제
          </button>
        </div>

        <div className="sidebar-section">
          <p className="sidebar-label">인기 질문</p>
          {supportProgramChatSuggestions.map((suggestion) => (
            <button key={suggestion} className="sidebar-prompt" onClick={() => handleSelectSuggestion(suggestion)}>
              {suggestion}
            </button>
          ))}
        </div>

        <div className="sidebar-section sidebar-stats">
          <p className="sidebar-label">공고 데이터</p>
          <div><strong>공식</strong><span>기업마당</span></div>
          <div><strong>{conversationCount}</strong><span>이번 대화 검색</span></div>
        </div>

        <p className="sidebar-note">검색 결과는 기업마당 공식 공고와 원문 링크를 기반으로 합니다.</p>
      </aside>

      <section className="chat-workspace" aria-label="GovBiz 지원사업 검색 채팅">
        <header className="chat-header">
          <button className="menu-button" aria-label="메뉴 열기" onClick={() => setIsSidebarOpen(true)}>☰</button>
          <div>
            <p className="header-eyebrow">지원사업 검색</p>
            <h1>GovBiz에게 물어보세요</h1>
          </div>
          <span className="data-status">기업마당 공식 데이터</span>
        </header>

        <div className="message-timeline" ref={timelineRef}>
          {messages.map((message) => (
            <article key={message.id} className={`message-row ${message.role}`}>
              {message.role === 'assistant' ? <span className="assistant-avatar" aria-hidden="true">G</span> : null}
              <div className="message-content">
                <div className="message-bubble">{message.text}</div>
                {message.id === messages[0]?.id ? (
                  <div className="suggestion-list" aria-label="추천 질문">
                    {supportProgramChatSuggestions.map((suggestion) => (
                      <button key={suggestion} onClick={() => handleSelectSuggestion(suggestion)}>{suggestion}</button>
                    ))}
                  </div>
                ) : null}
                {message.programs?.length ? (
                  <div className="program-list" aria-label="지원사업 검색 결과">
                    {message.programs.map((program) => <ProgramCard key={program.id} program={program} />)}
                  </div>
                ) : null}
              </div>
            </article>
          ))}
          {isSearching ? (
            <div className="message-row assistant">
              <span className="assistant-avatar" aria-hidden="true">G</span>
              <div className="message-bubble typing" aria-live="polite">공고를 찾아보고 있어요…</div>
            </div>
          ) : null}
        </div>

        <form className="chat-composer" onSubmit={handleSubmit}>
          {searchError ? <p className="chat-error" role="alert">{searchError}</p> : null}
          <label className="sr-only" htmlFor="chat-input">지원사업 검색 메시지</label>
          <textarea
            id="chat-input"
            value={draft}
            onChange={(event) => updateDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                event.currentTarget.form?.requestSubmit()
              }
            }}
            placeholder="예: 서울에서 AI 창업지원 사업을 찾아줘"
            rows={1}
          />
          <button type="submit" aria-label="메시지 보내기" disabled={!isReadyToSubmit}>↑</button>
          <small>Enter로 전송 · Shift+Enter로 줄바꿈</small>
        </form>
      </section>
    </main>
  )
}

function ProgramCard({ program }: { program: SupportProgram }) {
  return (
    <article className="program-card">
      <div className="program-card-topline">
        <span className="program-badge">관련 공고</span>
        <span className="program-deadline">{formatApplicationDeadline(program)}</span>
      </div>
      <h2>{program.title}</h2>
      <p className="program-organization">{program.organization}</p>
      <p className="program-summary">{program.summary}</p>
      <div className="program-meta">
        <span>{program.supportAmount}</span>
        <span>{program.targetDescription}</span>
      </div>
      <div className="program-reasons">
        {program.matchedReasons.map((reason) => <span key={reason}>✓ {reason}</span>)}
      </div>
      <div className="program-actions">
        <button type="button" onClick={() => window.alert('상세 화면은 다음 단계에서 연결됩니다.')}>상세 조건 보기</button>
        <a href={program.sourceUrl} target="_blank" rel="noreferrer">원문 보기 ↗</a>
      </div>
    </article>
  )
}

function formatApplicationDeadline(program: SupportProgram) {
  if (!program.applicationEndDate) return program.applicationPeriod

  const [, month, day] = program.applicationEndDate.split('-')
  return `마감 ${Number(month)}월 ${Number(day)}일`
}
