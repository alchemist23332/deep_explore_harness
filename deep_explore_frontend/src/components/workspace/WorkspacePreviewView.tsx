import {
  ExternalLink,
  FileText,
  MonitorPlay,
  RefreshCw,
  Square,
} from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import {
  getWorkspacePreview,
  getWorkspacePreviewLogs,
  stopWorkspacePreview,
  type WorkspacePreview,
} from '../../api/workspaces'

const stoppedPreview: WorkspacePreview = {
  status: 'STOPPED',
  url: null,
  containerPort: 3000,
  hostPort: null,
  logs: '',
}

export function WorkspacePreviewView({
  workspaceId,
  active,
}: {
  workspaceId: string
  active: boolean
}) {
  const [preview, setPreview] = useState(stoppedPreview)
  const [loading, setLoading] = useState(false)
  const [showLogs, setShowLogs] = useState(false)
  const [frameKey, setFrameKey] = useState(0)

  const refresh = useCallback(async () => {
    const next = await getWorkspacePreview(workspaceId)
    setPreview(next)
  }, [workspaceId])

  useEffect(() => {
    setPreview(stoppedPreview)
    setShowLogs(false)
  }, [workspaceId])

  useEffect(() => {
    if (!active) return
    void refresh().catch((error: Error) => toast.error(error.message))
    const timer = window.setInterval(() => {
      void refresh().catch(() => undefined)
    }, 2_000)
    return () => window.clearInterval(timer)
  }, [active, refresh])

  const stop = async () => {
    setLoading(true)
    try {
      setPreview(await stopWorkspacePreview(workspaceId))
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setLoading(false)
    }
  }

  const toggleLogs = async () => {
    const nextOpen = !showLogs
    setShowLogs(nextOpen)
    if (!nextOpen) return
    try {
      const result = await getWorkspacePreviewLogs(workspaceId)
      setPreview((current) => ({ ...current, logs: result.logs }))
    } catch (error) {
      toast.error((error as Error).message)
    }
  }

  return (
    <div
      className="workspace-preview-view"
      data-active={active}
      aria-hidden={!active}
    >
      <div className="workspace-preview-toolbar">
        <span className={`preview-state ${preview.status.toLowerCase()}`}>
          <i />
          {statusLabel(preview.status)}
        </span>
        <div>
          <button
            type="button"
            className="icon-button compact"
            onClick={() => void toggleLogs()}
            title="预览日志"
            aria-label="预览日志"
          >
            <FileText size={14} />
          </button>
          <button
            type="button"
            className="icon-button compact"
            disabled={!preview.url}
            onClick={() => setFrameKey((value) => value + 1)}
            title="重新加载预览"
            aria-label="重新加载预览"
          >
            <RefreshCw size={14} />
          </button>
          <button
            type="button"
            className="icon-button compact"
            disabled={!preview.url}
            onClick={() => {
              if (preview.url) {
                window.open(preview.url, '_blank', 'noopener,noreferrer')
              }
            }}
            title="在新窗口打开"
            aria-label="在新窗口打开"
          >
            <ExternalLink size={14} />
          </button>
          <button
            type="button"
            className="icon-button compact"
            disabled={preview.status === 'STOPPED' || loading}
            onClick={() => void stop()}
            title="停止预览"
            aria-label="停止预览"
          >
            <Square size={13} />
          </button>
        </div>
      </div>

      <div className="workspace-preview-content">
        {preview.url && preview.status === 'RUNNING' ? (
          <iframe
            key={`${preview.url}-${frameKey}`}
            src={preview.url}
            title="工作区应用预览"
            sandbox="allow-scripts allow-same-origin allow-forms allow-modals allow-pointer-lock allow-popups allow-downloads"
          />
        ) : (
          <div className="workspace-preview-empty">
            <MonitorPlay size={26} />
            <strong>{statusLabel(preview.status)}</strong>
          </div>
        )}
        {showLogs && (
          <pre className="workspace-preview-logs">
            {preview.logs || 'No preview logs.'}
          </pre>
        )}
      </div>
    </div>
  )
}

function statusLabel(status: WorkspacePreview['status']) {
  if (status === 'RUNNING') return '预览运行中'
  if (status === 'STARTING') return '预览启动中'
  if (status === 'FAILED') return '预览启动失败'
  return '暂无运行中的预览'
}
