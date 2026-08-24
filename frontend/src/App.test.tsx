// @vitest-environment jsdom

import { fireEvent, render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { describe, expect, it, vi } from 'vitest'

import App from './App'
import { createAppStore } from './app/store'

vi.mock('./presentation/shared/core-api-status/CoreApiConnectionStatus', () => ({
  CoreApiConnectionStatus: () => null,
}))

describe('App navigation', () => {
  it('채팅과 ViewModel 구조 예제 화면을 오갈 수 있다', () => {
    render(
      <Provider store={createAppStore()}>
        <App />
      </Provider>,
    )

    expect(screen.getByRole('heading', { name: 'GovBiz에게 물어보세요' })).toBeTruthy()
    const chatInput = screen.getByRole('textbox', { name: '지원사업 검색 메시지' })
    fireEvent.change(chatInput, { target: { value: '서울 AI 지원사업' } })

    fireEvent.click(screen.getByRole('button', { name: 'ViewModel 구조 예제' }))

    expect(screen.getByRole('heading', { name: '재사용 가능한 수직 슬라이스' })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '지원사업 채팅으로 돌아가기' }))

    expect(screen.getByRole('heading', { name: 'GovBiz에게 물어보세요' })).toBeTruthy()
    expect(
      (screen.getByRole('textbox', { name: '지원사업 검색 메시지' }) as HTMLTextAreaElement)
        .value,
    ).toBe('서울 AI 지원사업')
  })
})
