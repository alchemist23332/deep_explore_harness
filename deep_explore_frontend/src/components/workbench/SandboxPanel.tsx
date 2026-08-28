import * as Dialog from '@radix-ui/react-dialog'
import {
  FileCode2,
  Maximize2,
  Minimize2,
  MonitorPlay,
  Play,
  Plus,
  RefreshCw,
  Square,
  Terminal,
  Trash2,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import {
  createWorkspace,
  deleteWorkspace,
  getRuntimeOverview,
  listWorkspaces,
  startWorkspace,
  stopWorkspace,
  type RuntimeOverview,
  type SandboxWorkspace,
  type StarterTemplate,
} from '../../api/workspaces'
import { WorkspaceFilesView } from '../workspace/WorkspaceFilesView'
import { WorkspaceTerminalView } from '../workspace/WorkspaceTerminalView'
import { WorkspacePreviewView } from '../workspace/WorkspacePreviewView'
import { useSandboxWorkspace } from '../../runtime/sandbox-workspace-context'

export function SandboxPanel() {
  const {
    panelMaximized,
    setPanelMaximized,
    setPanelOpen,
    activeWorkspaceId,
    setActiveWorkspaceId,
    conversationWorkspaceId,
    activeTab,
    setActiveTab,
  } = useSandboxWorkspace()
  const [workspaces, setWorkspaces] = useState<SandboxWorkspace[]>([])
  const [runtime, setRuntime] = useState<RuntimeOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [newName, setNewName] = useState('Web Workspace')
  const [newTemplate, setNewTemplate] =
    useState<StarterTemplate>('WEB_TYPESCRIPT')
  const [lifecycleBusy, setLifecycleBusy] = useState(false)
  const [dirty, setDirty] = useState(false)
  const [commandRunning, setCommandRunning] = useState(false)

  const activeWorkspace = useMemo(
    () =>
      workspaces.find(
        (workspace) => workspace.id === activeWorkspaceId,
      ) ?? null,
    [activeWorkspaceId, workspaces],
  )

  const refresh = useCallback(async () => {
    const [nextWorkspaces, nextRuntime] = await Promise.all([
      listWorkspaces(),
      getRuntimeOverview(),
    ])
    setWorkspaces(nextWorkspaces)
    setRuntime(nextRuntime)
    if (
      activeWorkspaceId &&
      nextWorkspaces.some(
        (workspace) => workspace.id === activeWorkspaceId,
      )
    ) {
      return
    }
    setActiveWorkspaceId(nextWorkspaces[0]?.id ?? null)
  }, [activeWorkspaceId, setActiveWorkspaceId])

  useEffect(() => {
    let active = true
    setLoading(true)
    refresh()
      .catch((error: Error) => {
        if (active) toast.error(error.message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [refresh])

  useEffect(() => {
    if (commandRunning) return
    const timer = window.setInterval(() => {
      void refresh().catch(() => undefined)
    }, 10_000)
    return () => window.clearInterval(timer)
  }, [commandRunning, refresh])

  const chooseWorkspace = (workspaceId: string) => {
    if (workspaceId === activeWorkspaceId) return
    if (dirty && !window.confirm('当前文件尚未保存，确定切换工作区？')) {
      return
    }
    setDirty(false)
    setActiveWorkspaceId(workspaceId)
  }

  useEffect(() => {
    if (
      !conversationWorkspaceId ||
      conversationWorkspaceId === activeWorkspaceId ||
      dirty ||
      !workspaces.some(
        (workspace) => workspace.id === conversationWorkspaceId,
      )
    ) {
      return
    }
    setActiveWorkspaceId(conversationWorkspaceId)
  }, [
    activeWorkspaceId,
    conversationWorkspaceId,
    dirty,
    setActiveWorkspaceId,
    workspaces,
  ])

  const create = async () => {
    try {
      const created = await createWorkspace(newName, newTemplate)
      setWorkspaces((current) => [created, ...current])
      setActiveWorkspaceId(created.id)
      setCreateOpen(false)
      setNewName('Web Workspace')
      setNewTemplate('WEB_TYPESCRIPT')
      if (runtime?.available) {
        setLifecycleBusy(true)
        try {
          const started = await startWorkspace(created.id)
          setWorkspaces((current) =>
            current.map((workspace) =>
              workspace.id === started.id ? started : workspace,
            ),
          )
        } catch (startError) {
          toast.error(
            `工作区已创建，但沙箱启动失败：${
              (startError as Error).message
            }`,
          )
          await refresh()
        } finally {
          setLifecycleBusy(false)
        }
      }
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  const remove = async () => {
    if (!activeWorkspace) return
    if (dirty && !window.confirm('当前文件尚未保存，仍要删除工作区？')) {
      return
    }
    if (!window.confirm(`删除工作区“${activeWorkspace.name}”及其全部文件？`)) {
      return
    }
    try {
      await deleteWorkspace(activeWorkspace.id)
      const remaining = workspaces.filter(
        (workspace) => workspace.id !== activeWorkspace.id,
      )
      setWorkspaces(remaining)
      setActiveWorkspaceId(remaining[0]?.id ?? null)
      setDirty(false)
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  const ensureRunning = async (workspaceId: string) => {
    const workspace = workspaces.find(
      (candidate) => candidate.id === workspaceId,
    )
    if (workspace?.status === 'RUNNING') return
    if (!runtime?.available) {
      throw new Error(runtime?.message ?? 'Docker 不可用')
    }
    setLifecycleBusy(true)
    try {
      const updated = await startWorkspace(workspaceId)
      setWorkspaces((current) =>
        current.map((candidate) =>
          candidate.id === updated.id ? updated : candidate,
        ),
      )
    } catch (error) {
      await refresh()
      throw error
    } finally {
      setLifecycleBusy(false)
    }
  }

  const runLifecycle = async (action: 'start' | 'stop') => {
    if (!activeWorkspace) return
    try {
      if (action === 'start') {
        await ensureRunning(activeWorkspace.id)
        return
      }
      setLifecycleBusy(true)
      const updated = await stopWorkspace(activeWorkspace.id)
      setWorkspaces((current) =>
        current.map((workspace) =>
          workspace.id === updated.id ? updated : workspace,
        ),
      )
    } catch (error) {
      toast.error((error as Error).message)
      await refresh()
    } finally {
      if (action === 'stop') setLifecycleBusy(false)
    }
  }

  return (
    <section
      className={`embedded-sandbox-panel ${
        runtime?.available ? 'runtime-available' : 'runtime-unavailable'
      }`}
    >
      <header className="embedded-sandbox-toolbar">
        <div className="workspace-selector-wrap">
          <span
            className={`sandbox-state ${
              activeWorkspace?.status.toLowerCase() ?? 'stopped'
            }`}
          />
          <select
            value={activeWorkspaceId ?? ''}
            onChange={(event) => chooseWorkspace(event.target.value)}
            aria-label="当前沙箱工作区"
            title="当前沙箱工作区"
          >
            {workspaces.length === 0 && (
              <option value="">尚无工作区</option>
            )}
            {workspaces.map((workspace) => (
              <option key={workspace.id} value={workspace.id}>
                {workspace.name}
              </option>
            ))}
          </select>
        </div>

        <div className="embedded-sandbox-actions">
          <button
            type="button"
            className="icon-button compact"
            onClick={() => setCreateOpen(true)}
            title="新建工作区"
            aria-label="新建工作区"
          >
            <Plus size={15} />
          </button>
          {activeWorkspace?.status === 'RUNNING' ? (
            <button
              type="button"
              className="icon-button compact"
              disabled={lifecycleBusy}
              onClick={() => void runLifecycle('stop')}
              title="停止沙箱"
              aria-label="停止沙箱"
            >
              <Square size={14} />
            </button>
          ) : (
            <button
              type="button"
              className="icon-button compact"
              disabled={
                lifecycleBusy ||
                !activeWorkspace ||
                !runtime?.available
              }
              onClick={() => void runLifecycle('start')}
              title="启动沙箱"
              aria-label="启动沙箱"
            >
              <Play size={15} />
            </button>
          )}
          <button
            type="button"
            className="icon-button compact"
            disabled={loading}
            onClick={() => void refresh()}
            title="刷新工作区"
            aria-label="刷新工作区"
          >
            <RefreshCw size={15} />
          </button>
          <button
            type="button"
            className="icon-button compact"
            onClick={() => setPanelMaximized(!panelMaximized)}
            title={panelMaximized ? '恢复分栏' : '最大化工作区'}
            aria-label={panelMaximized ? '恢复分栏' : '最大化工作区'}
          >
            {panelMaximized ? (
              <Minimize2 size={15} />
            ) : (
              <Maximize2 size={15} />
            )}
          </button>
          <button
            type="button"
            className="icon-button compact"
            onClick={() => setPanelOpen(false)}
            title="关闭工作区"
            aria-label="关闭工作区"
          >
            <X size={16} />
          </button>
        </div>
      </header>

      {!runtime?.available && (
        <div className="embedded-runtime-warning">
          {runtime?.message ?? '正在检测 Docker'}
        </div>
      )}

      <div className="embedded-sandbox-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'files'}
          className={activeTab === 'files' ? 'active' : ''}
          onClick={() => setActiveTab('files')}
        >
          <FileCode2 size={14} />
          文件
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'terminal'}
          className={activeTab === 'terminal' ? 'active' : ''}
          onClick={() => setActiveTab('terminal')}
        >
          <Terminal size={14} />
          终端
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'preview'}
          className={activeTab === 'preview' ? 'active' : ''}
          onClick={() => setActiveTab('preview')}
        >
          <MonitorPlay size={14} />
          预览
        </button>
        {activeWorkspace && (
          <span className="sandbox-runtime-label">
            {activeWorkspace.runtimeProfile} ·{' '}
            {statusLabel(activeWorkspace.status)}
          </span>
        )}
        {activeWorkspace && (
          <button
            type="button"
            className="icon-button compact sandbox-toolbar-delete"
            onClick={() => void remove()}
            title="删除当前工作区"
            aria-label="删除当前工作区"
          >
            <Trash2 size={14} />
          </button>
        )}
      </div>

      <div className="embedded-sandbox-content">
        {activeWorkspace ? (
          <>
            <WorkspaceFilesView
              workspaceId={activeWorkspace.id}
              active={activeTab === 'files'}
              onDirtyChange={setDirty}
            />
            <WorkspaceTerminalView
              workspaceId={activeWorkspace.id}
              running={activeWorkspace.status === 'RUNNING'}
              active={activeTab === 'terminal'}
              lifecycleBusy={lifecycleBusy}
              onEnsureRunning={() =>
                ensureRunning(activeWorkspace.id)
              }
              onCommandRunningChange={setCommandRunning}
            />
            <WorkspacePreviewView
              workspaceId={activeWorkspace.id}
              active={activeTab === 'preview'}
            />
          </>
        ) : (
          <div className="embedded-sandbox-empty">
            <FileCode2 size={24} />
            <strong>创建 Fullstack 工作区</strong>
            <button
              type="button"
              className="button primary"
              onClick={() => setCreateOpen(true)}
            >
              <Plus size={14} />
              新建工作区
            </button>
          </div>
        )}
      </div>

      <Dialog.Root open={createOpen} onOpenChange={setCreateOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="dialog-overlay" />
          <Dialog.Content className="dialog-content">
            <Dialog.Title>新建沙箱工作区</Dialog.Title>
            <Dialog.Description>
              创建持久化项目目录和隔离运行环境。
            </Dialog.Description>
            <input
              className="dialog-input"
              value={newName}
              maxLength={80}
              onChange={(event) => setNewName(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') void create()
              }}
              autoFocus
            />
            <select
              className="dialog-input"
              value={newTemplate}
              onChange={(event) =>
                setNewTemplate(
                  event.target.value as StarterTemplate,
                )
              }
              aria-label="项目模板"
            >
              <option value="WEB_TYPESCRIPT">
                Web TypeScript
              </option>
              <option value="JAVA_MAVEN">Java Maven</option>
              <option value="EMPTY">空白项目</option>
            </select>
            <div className="dialog-actions">
              <Dialog.Close asChild>
                <button type="button" className="button secondary">
                  取消
                </button>
              </Dialog.Close>
              <button
                type="button"
                className="button primary"
                onClick={() => void create()}
              >
                创建
              </button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </section>
  )
}

function statusLabel(status: SandboxWorkspace['status']) {
  if (status === 'RUNNING') return '运行中'
  if (status === 'STARTING') return '启动中'
  if (status === 'STOPPING') return '停止中'
  if (status === 'ERROR') return '异常'
  return '已停止'
}
