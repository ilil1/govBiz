import { useState } from 'react'
import { Provider } from 'react-redux'

import { ChatPage } from './presentation/features/chat/view/ChatPage'
import { createSampleItemStore } from './presentation/features/sample-item/state/sampleItemStore'
import { ReduxSampleItemPage } from './presentation/features/sample-item/view/ReduxSampleItemPage'
import { SampleItemPage } from './presentation/features/sample-item/view/SampleItemPage'

type AppPage = 'chat' | 'sample-item-hook' | 'sample-item-redux'

/** GovBiz의 첫 진입점은 공고를 찾는 채팅 화면입니다. */
function App() {
  const [currentPage, setCurrentPage] = useState<AppPage>('chat')
  const [sampleItemStore] = useState(createSampleItemStore)

  if (currentPage === 'sample-item-hook') {
    return (
      <SampleItemPage
        onBackToChat={() => setCurrentPage('chat')}
        onOpenReduxVersion={() => setCurrentPage('sample-item-redux')}
      />
    )
  }

  if (currentPage === 'sample-item-redux') {
    return (
      <Provider store={sampleItemStore}>
        <ReduxSampleItemPage
          onBackToChat={() => setCurrentPage('chat')}
          onOpenHookVersion={() => setCurrentPage('sample-item-hook')}
        />
      </Provider>
    )
  }

  return <ChatPage onOpenSampleItem={() => setCurrentPage('sample-item-hook')} />
}

export default App
