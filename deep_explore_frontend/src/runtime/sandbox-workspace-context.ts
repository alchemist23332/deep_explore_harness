import { createContext, useContext } from 'react'

export type SandboxTab = 'files' | 'terminal'

export interface SandboxWorkspaceContextValue {
  panelOpen: boolean
  setPanelOpen: (open: boolean) => void
  panelMaximized: boolean
  setPanelMaximized: (maximized: boolean) => void
  panelSize: number
  setPanelSize: (size: number) => void
  activeWorkspaceId: string | null
  setActiveWorkspaceId: (workspaceId: string | null) => void
  activeTab: SandboxTab
  setActiveTab: (tab: SandboxTab) => void
  selectedFilePath: string | null
  setSelectedFilePath: (path: string | null) => void
}

export const SandboxWorkspaceContext =
  createContext<SandboxWorkspaceContextValue | null>(null)

export function useSandboxWorkspace() {
  const value = useContext(SandboxWorkspaceContext)
  if (!value) {
    throw new Error(
      'useSandboxWorkspace must be used inside WorkbenchSandboxProvider',
    )
  }
  return value
}
