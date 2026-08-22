import {
  type DataMessagePartProps,
  useAuiState,
} from '@assistant-ui/react'
import {
  Ban,
  CheckCircle2,
  ChevronDown,
  CircleX,
  ListChecks,
  LoaderCircle,
  Search,
  Wrench,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import type {
  RunActivityData,
  ToolActivity,
} from '../../types/tool-activity'

export function ToolActivityTimeline({
  data,
}: DataMessagePartProps<RunActivityData>) {
  const activity = data as RunActivityData
  const isMessageRunning = useAuiState(
    (state) => state.message.status?.type === 'running',
  )
  const [open, setOpen] = useState(isMessageRunning)
  const isActive = activity.tools.some(
    (tool: ToolActivity) => tool.status === 'RUNNING',
  )
  const hasFailure = activity.tools.some(
    (tool: ToolActivity) => tool.status === 'FAILED',
  )

  useEffect(() => {
    setOpen(isMessageRunning)
  }, [isMessageRunning])

  return (
    <details
      className="tool-activity"
      open={open}
      onToggle={(event) => setOpen(event.currentTarget.open)}
    >
      <summary className="tool-activity-summary">
        <span className="tool-activity-heading">
          <ListChecks size={14} />
          <span>执行过程</span>
        </span>
        <span className="tool-activity-summary-status">
          {isActive ? '进行中' : hasFailure ? '部分失败' : '已完成'}
          <ChevronDown size={14} />
        </span>
      </summary>

      <div className="tool-activity-list">
        {activity.tools.map((tool: ToolActivity) => (
          <ToolActivityRow key={tool.toolCallId} tool={tool} />
        ))}
      </div>
    </details>
  )
}

function ToolActivityRow({ tool }: { tool: ToolActivity }) {
  return (
    <div className="tool-activity-row">
      <div
        className={`tool-activity-status tool-activity-status-${tool.status.toLowerCase()}`}
        aria-label={statusLabel(tool)}
      >
        <StatusIcon tool={tool} />
      </div>
      <div className="tool-activity-copy">
        <div className="tool-activity-title">
          <ToolIcon toolName={tool.toolName} />
          <span>{tool.displayName}</span>
          {tool.durationMs !== null && (
            <span className="tool-activity-duration">
              {formatDuration(tool.durationMs)}
            </span>
          )}
        </div>
        <div className="tool-activity-detail">{tool.summary}</div>
      </div>
    </div>
  )
}

function StatusIcon({ tool }: { tool: ToolActivity }) {
  switch (tool.status) {
    case 'RUNNING':
      return <LoaderCircle className="tool-activity-spinner" size={15} />
    case 'SUCCEEDED':
      return <CheckCircle2 size={15} />
    case 'FAILED':
      return <CircleX size={15} />
    case 'CANCELLED':
      return <Ban size={15} />
  }
}

function ToolIcon({ toolName }: { toolName: string }) {
  return toolName === 'web_search'
    ? <Search size={13} />
    : <Wrench size={13} />
}

function statusLabel(tool: ToolActivity) {
  switch (tool.status) {
    case 'RUNNING':
      return `${tool.displayName}正在执行`
    case 'SUCCEEDED':
      return `${tool.displayName}执行完成`
    case 'FAILED':
      return `${tool.displayName}执行失败`
    case 'CANCELLED':
      return `${tool.displayName}已取消`
  }
}

function formatDuration(durationMs: number) {
  return durationMs < 1_000
    ? `${Math.round(durationMs)}ms`
    : `${(durationMs / 1_000).toFixed(1)}s`
}
