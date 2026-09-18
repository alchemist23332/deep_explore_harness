import type {
  RunActivityData,
  ToolActivityStatus,
} from '../types/tool-activity'

export type ChatEventType =
  | 'metadata'
  | 'delta'
  | 'tool_start'
  | 'tool_end'
  | 'approval_required'
  | 'artifact'
  | 'done'
  | 'error'
export type AgentMode = 'FAST' | 'DEEP'
export type SearchProvider = 'JINA' | 'TAVILY'

export interface ChatEvent {
  type: ChatEventType
  conversationId: string
  content: string
  runId: string | null
  sequence: number | null
  occurredAt: string | null
  assistantMessageId: string | null
  tool: {
    toolCallId: string
    toolName: string
    displayName: string
    status: ToolActivityStatus
    summary: string
    provider: string | null
  } | null
}

export interface ChatRequest {
  conversationId: string | null
  message: string
  mode: AgentMode
  userMessageId: string | null
  userParentMessageId: string | null
  assistantMessageId: string | null
  searchProvider: SearchProvider
  workspaceId: string | null
  agentId?: string | null
  profileId?: string | null
}

interface RunStartResponse {
  runId: string
  conversationId: string
  assistantMessageId: string
  status: string
}

export interface RuntimeConfig {
  provider: string
  fastModel: string
  deepModel: string
  webSearchEnabled: boolean
  defaultSearchProvider: SearchProvider
  availableSearchProviders: SearchProvider[]
  availableAgents: string[]
  profiles: Array<{
    id: string
    displayName: string
    model: string
  }>
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

export async function* streamChatEvents(
  request: ChatRequest,
  signal?: AbortSignal,
): AsyncGenerator<ChatEvent> {
  const started = await startRun(request, signal)
  try {
    yield* readEventStream(
      `${apiBaseUrl}/api/runs/${started.runId}/events`,
      signal,
    )
  } catch (error) {
    if (signal?.aborted) {
      await cancelRun(started.runId)
    }
    throw error
  }
}

async function startRun(
  request: ChatRequest,
  signal?: AbortSignal,
): Promise<RunStartResponse> {
  const response = await fetch(`${apiBaseUrl}/api/runs`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
    signal,
  })

  if (!response.ok) {
    throw new Error(await toRequestError(response))
  }
  return response.json() as Promise<RunStartResponse>
}

async function* readEventStream(
  url: string,
  signal?: AbortSignal,
): AsyncGenerator<ChatEvent> {
  const response = await fetch(url, {
    headers: { Accept: 'text/event-stream' },
    signal,
  })
  if (!response.ok) {
    throw new Error(await toRequestError(response))
  }
  if (!response.body) {
    throw new Error('浏览器未收到流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() ?? ''

    for (const block of blocks) {
      const event = parseEvent(block)
      if (event) {
        yield event
      }
    }

    if (done) {
      if (buffer.trim()) {
        const event = parseEvent(buffer)
        if (event) {
          yield event
        }
      }
      break
    }
  }
}

async function cancelRun(runId: string): Promise<void> {
  await fetch(`${apiBaseUrl}/api/runs/${runId}/cancel`, {
    method: 'POST',
  })
}

export async function getRuntimeConfig(): Promise<RuntimeConfig> {
  const response = await fetch(`${apiBaseUrl}/api/config`)
  if (!response.ok) {
    throw new Error('无法读取模型配置')
  }
  return response.json() as Promise<RuntimeConfig>
}

export async function checkHealth(): Promise<boolean> {
  try {
    const response = await fetch(`${apiBaseUrl}/api/health`)
    return response.ok
  } catch {
    return false
  }
}

export async function getRunActivities(
  conversationId: string,
): Promise<RunActivityData[]> {
  const response = await fetch(
    `${apiBaseUrl}/api/conversations/${conversationId}/run-activities`,
  )
  if (!response.ok) {
    throw new Error('无法读取执行过程')
  }
  return response.json() as Promise<RunActivityData[]>
}

function parseEvent(block: string): ChatEvent | null {
  const data = block
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  return data ? (JSON.parse(data) as ChatEvent) : null
}

async function toRequestError(response: Response): Promise<string> {
  if (response.status === 401) return 'API Key 无效或已失效'
  if (response.status === 402) return '模型账户余额不足'
  if (response.status === 429) return '请求过于频繁，请稍后重试'
  if (response.status >= 500) return '模型服务暂时不可用'

  const body = await response.text()
  return body || `请求失败（HTTP ${response.status}）`
}
