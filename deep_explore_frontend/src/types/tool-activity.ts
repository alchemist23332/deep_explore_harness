export type ToolActivityStatus =
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'

export type RunActivityStatus =
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'
  | 'WAITING_APPROVAL'

export interface ToolActivity {
  toolCallId: string
  toolName: string
  displayName: string
  status: ToolActivityStatus
  summary: string
  provider: string | null
  startedAt: string | null
  completedAt: string | null
  durationMs: number | null
}

export interface RunActivityData {
  runId: string
  assistantMessageId: string | null
  workspaceId: string | null
  status: RunActivityStatus
  tools: ToolActivity[]
}
