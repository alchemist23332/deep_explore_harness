import {
  useCallback,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  SandboxWorkspaceContext,
  type SandboxTab,
  type SandboxWorkspaceContextValue,
} from './sandbox-workspace-context'

const storageKeys = {
  panelOpen: 'deep-explore:sandbox:panel-open',
  panelSize: 'deep-explore:sandbox:panel-size',
  activeWorkspace: 'deep-explore:sandbox:active-workspace',
  activeTab: 'deep-explore:sandbox:active-tab',
}

export function WorkbenchSandboxProvider({
  children,
}: PropsWithChildren) {
  const [searchParams] = useSearchParams()
  const [panelOpen, setPanelOpenState] = useState(
    () =>
      searchParams.get('sandbox') === 'open' ||
      readBoolean(storageKeys.panelOpen, window.innerWidth > 820),
  )
  const [panelMaximized, setPanelMaximized] = useState(false)
  const [panelSize, setPanelSizeState] = useState(() =>
    readNumber(storageKeys.panelSize, 36, 28, 65),
  )
  const [activeWorkspaceId, setActiveWorkspaceIdState] = useState<
    string | null
  >(
    () =>
      searchParams.get('workspace') ??
      window.localStorage.getItem(storageKeys.activeWorkspace),
  )
  const [conversationWorkspaceId, setConversationWorkspaceId] =
    useState<string | null>(null)
  const [activeTab, setActiveTabState] = useState<SandboxTab>(() => {
    const saved = window.localStorage.getItem(storageKeys.activeTab)
    return saved === 'terminal' || saved === 'preview'
      ? saved
      : 'files'
  })
  const [selectedFilePath, setSelectedFilePathState] = useState<
    string | null
  >(() => readSelectedFile(activeWorkspaceId))

  const setPanelOpen = useCallback((open: boolean) => {
    setPanelOpenState(open)
    if (!open) setPanelMaximized(false)
    window.localStorage.setItem(storageKeys.panelOpen, String(open))
  }, [])

  const setPanelSize = useCallback((size: number) => {
    const next = Math.min(65, Math.max(28, size))
    setPanelSizeState(next)
    window.localStorage.setItem(storageKeys.panelSize, String(next))
  }, [])

  const setActiveWorkspaceId = useCallback(
    (workspaceId: string | null) => {
      setActiveWorkspaceIdState(workspaceId)
      setSelectedFilePathState(readSelectedFile(workspaceId))
      if (workspaceId) {
        window.localStorage.setItem(
          storageKeys.activeWorkspace,
          workspaceId,
        )
      } else {
        window.localStorage.removeItem(storageKeys.activeWorkspace)
      }
    },
    [],
  )

  const setActiveTab = useCallback((tab: SandboxTab) => {
    setActiveTabState(tab)
    window.localStorage.setItem(storageKeys.activeTab, tab)
  }, [])

  const setSelectedFilePath = useCallback(
    (path: string | null) => {
      setSelectedFilePathState(path)
      if (!activeWorkspaceId) return
      const key = selectedFileKey(activeWorkspaceId)
      if (path) window.localStorage.setItem(key, path)
      else window.localStorage.removeItem(key)
    },
    [activeWorkspaceId],
  )

  const value = useMemo<SandboxWorkspaceContextValue>(
    () => ({
      panelOpen,
      setPanelOpen,
      panelMaximized,
      setPanelMaximized,
      panelSize,
      setPanelSize,
      activeWorkspaceId,
      setActiveWorkspaceId,
      conversationWorkspaceId,
      setConversationWorkspaceId,
      activeTab,
      setActiveTab,
      selectedFilePath,
      setSelectedFilePath,
    }),
    [
      activeTab,
      activeWorkspaceId,
      conversationWorkspaceId,
      panelMaximized,
      panelOpen,
      panelSize,
      selectedFilePath,
      setActiveTab,
      setActiveWorkspaceId,
      setPanelOpen,
      setPanelSize,
      setSelectedFilePath,
    ],
  )

  return (
    <SandboxWorkspaceContext.Provider value={value}>
      {children}
    </SandboxWorkspaceContext.Provider>
  )
}

function readBoolean(key: string, fallback: boolean) {
  const value = window.localStorage.getItem(key)
  if (value === 'true') return true
  if (value === 'false') return false
  return fallback
}

function readNumber(
  key: string,
  fallback: number,
  minimum: number,
  maximum: number,
) {
  const stored = window.localStorage.getItem(key)
  if (stored === null || stored.trim() === '') return fallback
  const value = Number(stored)
  if (!Number.isFinite(value)) return fallback
  return Math.min(maximum, Math.max(minimum, value))
}

function readSelectedFile(workspaceId: string | null) {
  return workspaceId
    ? window.localStorage.getItem(selectedFileKey(workspaceId))
    : null
}

function selectedFileKey(workspaceId: string) {
  return `deep-explore:sandbox:selected-file:${workspaceId}`
}
