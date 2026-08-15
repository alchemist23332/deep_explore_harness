import * as Dialog from '@radix-ui/react-dialog'
import * as Tooltip from '@radix-ui/react-tooltip'
import { X } from 'lucide-react'
import { useState } from 'react'
import { Toaster } from 'sonner'
import './App.css'
import { ChatThread } from './components/chat/ChatThread'
import { AppHeader } from './components/layout/AppHeader'
import { AppSidebar } from './components/layout/AppSidebar'

function App() {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <Tooltip.Provider delayDuration={350}>
      <div className="app-frame">
        <div className="desktop-sidebar">
          <AppSidebar />
        </div>

        <main className="workspace">
          <AppHeader onOpenSidebar={() => setSidebarOpen(true)} />
          <ChatThread />
        </main>
      </div>

      <Dialog.Root open={sidebarOpen} onOpenChange={setSidebarOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="mobile-sidebar-overlay" />
          <Dialog.Content className="mobile-sidebar-content">
            <Dialog.Title className="sr-only">会话列表</Dialog.Title>
            <Dialog.Close asChild>
              <button
                type="button"
                className="icon-button mobile-sidebar-close"
                aria-label="关闭会话列表"
              >
                <X size={19} />
              </button>
            </Dialog.Close>
            <AppSidebar onNavigate={() => setSidebarOpen(false)} />
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      <Toaster position="top-center" richColors />
    </Tooltip.Provider>
  )
}

export default App
