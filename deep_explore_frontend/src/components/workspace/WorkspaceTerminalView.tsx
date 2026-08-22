import { XtermTerminal } from './XtermTerminal'

export function WorkspaceTerminalView({
  workspaceId,
  active,
  lifecycleBusy,
  onEnsureRunning,
  onCommandRunningChange,
}: {
  workspaceId: string
  running: boolean
  active: boolean
  lifecycleBusy: boolean
  onEnsureRunning: () => Promise<void>
  onCommandRunningChange: (running: boolean) => void
}) {
  return (
    <div
      className="workspace-terminal-view"
      data-active={active}
      aria-hidden={!active}
    >
      <XtermTerminal
        workspaceId={workspaceId}
        active={active}
        lifecycleBusy={lifecycleBusy}
        onEnsureRunning={onEnsureRunning}
        onConnectionBusyChange={onCommandRunningChange}
      />
    </div>
  )
}
