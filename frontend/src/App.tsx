import { useState } from 'react'

import { ChatPage } from './presentation/features/chat/view/ChatPage'
import { SampleItemPage } from './presentation/features/sample-item/view/SampleItemPage'

type AppPage = 'chat' | 'sample-item'

/** GovBiz의 첫 진입점은 공고를 찾는 채팅 화면입니다. */
function App() {
  const [currentPage, setCurrentPage] = useState<AppPage>('chat')

  if (currentPage === 'sample-item') {
    return <SampleItemPage onBackToChat={() => setCurrentPage('chat')} />
  }

  return <ChatPage onOpenSampleItem={() => setCurrentPage('sample-item')} />
}

export default App
