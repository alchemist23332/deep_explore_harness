import { createContext, useContext } from 'react'
import type { AgentMode, RuntimeConfig } from '../api/chat'

export type Theme = 'light' | 'dark'

export interface WorkspaceContextValue {
  mode: AgentMode
  setMode: (mode: AgentMode) => void
  config: RuntimeConfig | null
  serviceAvailable: boolean
  theme: Theme
  toggleTheme: () => void
}

export const WorkspaceContext = createContext<WorkspaceContextValue | null>(
  null,
)

export function useWorkspace() {
  const value = useContext(WorkspaceContext)
  if (!value) {
    throw new Error('useWorkspace must be used inside DeepExploreRuntimeProvider')
  }
  return value
}
