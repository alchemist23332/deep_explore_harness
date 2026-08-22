import * as Dialog from '@radix-ui/react-dialog'
import * as Tooltip from '@radix-ui/react-tooltip'
import { X } from 'lucide-react'
import { useEffect, useState } from 'react'
import {
  Navigate,
  Route,
  Routes,
  useNavigate,
  useParams,
} from 'react-router-dom'
import { Toaster } from 'sonner'
import './App.css'
import { AppSidebar } from './components/layout/AppSidebar'
import { WorkbenchShell } from './components/workbench/WorkbenchShell'
import { useSandboxWorkspace } from './runtime/sandbox-workspace-context'

function App() {
  return (
    <Tooltip.Provider delayDuration={350}>
      <Routes>
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/workspaces" element={<LegacyWorkspaceRedirect />} />
        <Route
          path="/workspaces/:workspaceId"
          element={<LegacyWorkspaceRedirect />}
        />
        <Route path="*" element={<Navigate to="/chat" replace />} />
      </Routes>
      <Toaster position="top-center" richColors />
    </Tooltip.Provider>
  )
}

function ChatPage() {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <>
      <div className="app-frame">
        <div className="desktop-sidebar">
          <AppSidebar />
        </div>

        <WorkbenchShell onOpenSidebar={() => setSidebarOpen(true)} />
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
    </>
  )
}

function LegacyWorkspaceRedirect() {
  const { workspaceId } = useParams()
  const navigate = useNavigate()
  const { setPanelOpen, setActiveWorkspaceId } = useSandboxWorkspace()

  useEffect(() => {
    if (workspaceId) setActiveWorkspaceId(workspaceId)
    setPanelOpen(true)
    navigate('/chat', { replace: true })
  }, [
    navigate,
    setActiveWorkspaceId,
    setPanelOpen,
    workspaceId,
  ])

  return null
}

export default App
