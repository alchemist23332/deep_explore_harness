import {
  Group,
  Panel,
  Separator,
  useGroupRef,
  usePanelRef,
  type Layout,
} from 'react-resizable-panels'
import { useEffect, useState } from 'react'
import { ChatThread } from '../chat/ChatThread'
import { AppHeader } from '../layout/AppHeader'
import { SandboxPanel } from './SandboxPanel'
import { useSandboxWorkspace } from '../../runtime/sandbox-workspace-context'

export function WorkbenchShell({
  onOpenSidebar,
}: {
  onOpenSidebar: () => void
}) {
  const mobile = useMediaQuery('(max-width: 820px)')
  return mobile ? (
    <MobileWorkbench onOpenSidebar={onOpenSidebar} />
  ) : (
    <DesktopWorkbench onOpenSidebar={onOpenSidebar} />
  )
}

function DesktopWorkbench({
  onOpenSidebar,
}: {
  onOpenSidebar: () => void
}) {
  const {
    panelOpen,
    setPanelOpen,
    panelMaximized,
    setPanelMaximized,
    panelSize,
    setPanelSize,
  } = useSandboxWorkspace()
  const groupRef = useGroupRef()
  const chatRef = usePanelRef()
  const sandboxRef = usePanelRef()

  useEffect(() => {
    if (panelMaximized) {
      groupRef.current?.setLayout({ chat: 0, sandbox: 100 })
      return
    }
    if (!panelOpen) {
      sandboxRef.current?.collapse()
      return
    }
    groupRef.current?.setLayout({
      chat: 100 - panelSize,
      sandbox: panelSize,
    })
  }, [
    groupRef,
    panelMaximized,
    panelOpen,
    panelSize,
    sandboxRef,
  ])

  const handleLayoutChanged = (
    layout: Layout,
    meta: { isUserInteraction: boolean },
  ) => {
    if (!meta.isUserInteraction) return
    const sandboxSize = layout.sandbox ?? 0
    if (sandboxSize < 1) {
      setPanelOpen(false)
      return
    }
    setPanelOpen(true)
    setPanelMaximized(false)
    setPanelSize(sandboxSize)
  }

  return (
    <main className="workbench-host">
      <Group
        id="deep-explore-workbench"
        orientation="horizontal"
        className="workbench-group"
        groupRef={groupRef}
        defaultLayout={{
          chat: panelOpen ? 100 - panelSize : 100,
          sandbox: panelOpen ? panelSize : 0,
        }}
        onLayoutChanged={handleLayoutChanged}
      >
        <Panel
          id="chat"
          panelRef={chatRef}
          minSize={panelMaximized ? '0%' : '38%'}
          collapsible
          collapsedSize="0%"
          className="workbench-chat-panel"
        >
          <section className="workspace">
            <AppHeader onOpenSidebar={onOpenSidebar} />
            <ChatThread />
          </section>
        </Panel>

        <Separator
          id="chat-sandbox-separator"
          className="workbench-resize-handle"
          disabled={!panelOpen || panelMaximized}
          data-open={panelOpen && !panelMaximized}
        >
          <span />
        </Separator>

        <Panel
          id="sandbox"
          panelRef={sandboxRef}
          defaultSize={`${panelSize}%`}
          minSize={panelMaximized ? '100%' : '28%'}
          maxSize={panelMaximized ? '100%' : '65%'}
          collapsible
          collapsedSize="0%"
          className="workbench-sandbox-panel"
        >
          <SandboxPanel />
        </Panel>
      </Group>
    </main>
  )
}

function MobileWorkbench({
  onOpenSidebar,
}: {
  onOpenSidebar: () => void
}) {
  const { panelOpen, setPanelOpen } = useSandboxWorkspace()

  return (
    <main className="workbench-host">
      <section className="workspace">
        <AppHeader onOpenSidebar={onOpenSidebar} />
        <ChatThread />
      </section>

      <button
        type="button"
        className={`mobile-sandbox-overlay ${
          panelOpen ? 'open' : 'closed'
        }`}
        onClick={() => setPanelOpen(false)}
        aria-label="关闭工作区"
        tabIndex={panelOpen ? 0 : -1}
      />
      <aside
        className={`mobile-sandbox-drawer ${
          panelOpen ? 'open' : 'closed'
        }`}
        role="dialog"
        aria-label="沙箱工作区"
        aria-modal={panelOpen}
        aria-hidden={!panelOpen}
        inert={!panelOpen}
      >
        <SandboxPanel />
      </aside>
    </main>
  )
}

function useMediaQuery(query: string) {
  const [matches, setMatches] = useState(
    () => window.matchMedia(query).matches,
  )

  useEffect(() => {
    const media = window.matchMedia(query)
    const update = () => setMatches(media.matches)
    update()
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [query])

  return matches
}
