const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export type WorkspaceStatus =
  | 'STOPPED'
  | 'STARTING'
  | 'RUNNING'
  | 'STOPPING'
  | 'ERROR'

export interface SandboxWorkspace {
  id: string
  name: string
  runtimeProfile: 'FULLSTACK'
  status: WorkspaceStatus
  lastError: string | null
  createdAt: string
  updatedAt: string
  lastStartedAt: string | null
}

export type StarterTemplate =
  | 'WEB_TYPESCRIPT'
  | 'JAVA_MAVEN'
  | 'EMPTY'

export type PreviewStatus =
  | 'STOPPED'
  | 'STARTING'
  | 'RUNNING'
  | 'FAILED'

export interface WorkspacePreview {
  status: PreviewStatus
  url: string | null
  containerPort: number
  hostPort: number | null
  logs: string
}

export interface RuntimeOverview {
  enabled: boolean
  available: boolean
  message: string
  profiles: Array<{
    id: string
    displayName: string
    description: string
  }>
}

export interface WorkspaceEntry {
  name: string
  path: string
  type: 'FILE' | 'DIRECTORY'
  size: number
  modifiedAt: string
}

export interface WorkspaceFile {
  path: string
  content: string
  size: number
  modifiedAt: string
  revision: string
}

export interface WorkspaceTreeNode {
  entry: WorkspaceEntry
  children: WorkspaceTreeNode[] | null
}

export interface WorkspaceChange {
  kind: 'CREATED' | 'MODIFIED' | 'DELETED' | 'OVERFLOW'
  path: string
  parentPath: string
  occurredAt: string
}

export interface CommandResult {
  command: string
  exitCode: number | null
  stdout: string
  stderr: string
  durationMs: number
  timedOut: boolean
  truncated: boolean
}

export async function getRuntimeOverview() {
  return request<RuntimeOverview>('/api/runtime-profiles')
}

export async function listWorkspaces() {
  return request<SandboxWorkspace[]>('/api/workspaces')
}

export async function getWorkspace(workspaceId: string) {
  return request<SandboxWorkspace>(`/api/workspaces/${workspaceId}`)
}

export async function createWorkspace(
  name: string,
  starterTemplate: StarterTemplate,
) {
  return request<SandboxWorkspace>('/api/workspaces', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name,
      runtimeProfile: 'FULLSTACK',
      starterTemplate,
    }),
  })
}

export async function startWorkspace(workspaceId: string) {
  return request<SandboxWorkspace>(`/api/workspaces/${workspaceId}/start`, {
    method: 'POST',
  })
}

export async function stopWorkspace(workspaceId: string) {
  return request<SandboxWorkspace>(`/api/workspaces/${workspaceId}/stop`, {
    method: 'POST',
  })
}

export async function deleteWorkspace(workspaceId: string) {
  await request<void>(`/api/workspaces/${workspaceId}`, {
    method: 'DELETE',
  })
}

export async function listWorkspaceFiles(
  workspaceId: string,
  path = '',
) {
  const query = new URLSearchParams({ path })
  return request<WorkspaceEntry[]>(
    `/api/workspaces/${workspaceId}/files?${query}`,
  )
}

export async function getWorkspaceTree(workspaceId: string) {
  return request<WorkspaceTreeNode[]>(
    `/api/workspaces/${workspaceId}/tree`,
  )
}

export async function createWorkspaceEntry(
  workspaceId: string,
  parentPath: string,
  name: string,
  type: WorkspaceEntry['type'],
) {
  return request<WorkspaceEntry>(
    `/api/workspaces/${workspaceId}/entries`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ parentPath, name, type }),
    },
  )
}

export async function moveWorkspaceEntry(
  workspaceId: string,
  sourcePath: string,
  targetPath: string,
) {
  return request<WorkspaceEntry>(
    `/api/workspaces/${workspaceId}/entries`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sourcePath, targetPath }),
    },
  )
}

export async function deleteWorkspaceEntry(
  workspaceId: string,
  path: string,
  recursive = false,
) {
  const query = new URLSearchParams({
    path,
    recursive: String(recursive),
  })
  await request<void>(
    `/api/workspaces/${workspaceId}/entries?${query}`,
    { method: 'DELETE' },
  )
}

export async function readWorkspaceFile(
  workspaceId: string,
  path: string,
) {
  const query = new URLSearchParams({ path })
  return request<WorkspaceFile>(
    `/api/workspaces/${workspaceId}/file?${query}`,
  )
}

export async function writeWorkspaceFile(
  workspaceId: string,
  path: string,
  content: string,
  expectedRevision?: string,
) {
  return request<WorkspaceFile>(`/api/workspaces/${workspaceId}/file`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path, content, expectedRevision }),
  })
}

export async function uploadWorkspaceFile(
  workspaceId: string,
  file: File,
  path?: string,
) {
  const form = new FormData()
  form.append('file', file)
  const query = path ? `?${new URLSearchParams({ path })}` : ''
  return request<{ path: string; size: number }>(
    `/api/workspaces/${workspaceId}/upload${query}`,
    { method: 'POST', body: form },
  )
}

export async function importWorkspaceZip(
  workspaceId: string,
  file: File,
) {
  const form = new FormData()
  form.append('file', file)
  return request<{ files: number; extractedBytes: number }>(
    `/api/workspaces/${workspaceId}/import/zip`,
    { method: 'POST', body: form },
  )
}

export async function executeWorkspaceCommand(
  workspaceId: string,
  command: string,
  workingDirectory = '',
) {
  return request<CommandResult>(
    `/api/workspaces/${workspaceId}/commands`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ command, workingDirectory }),
    },
  )
}

export async function getWorkspacePreview(workspaceId: string) {
  return request<WorkspacePreview>(
    `/api/workspaces/${workspaceId}/preview`,
  )
}

export async function startWorkspacePreview(
  workspaceId: string,
  command: string,
  workingDirectory = '',
  healthPath = '/',
) {
  return request<WorkspacePreview>(
    `/api/workspaces/${workspaceId}/preview/start`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        command,
        workingDirectory,
        healthPath,
      }),
    },
  )
}

export async function stopWorkspacePreview(workspaceId: string) {
  return request<WorkspacePreview>(
    `/api/workspaces/${workspaceId}/preview/stop`,
    { method: 'POST' },
  )
}

export async function getWorkspacePreviewLogs(workspaceId: string) {
  return request<{ logs: string }>(
    `/api/workspaces/${workspaceId}/preview/logs`,
  )
}

export function workspaceEventsUrl(workspaceId: string) {
  return `${API_BASE}/api/workspaces/${workspaceId}/events`
}

export function workspaceTerminalUrl(workspaceId: string) {
  const url = new URL(`${API_BASE}/api/workspaces/${workspaceId}/terminal`)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init)
  if (!response.ok) {
    const error = await response
      .json()
      .catch(() => ({ message: `HTTP ${response.status}` }))
    throw new Error(error.message ?? `HTTP ${response.status}`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
