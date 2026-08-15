export type ChatEventType = 'metadata' | 'delta' | 'done' | 'error'
export type AgentMode = 'FAST' | 'DEEP'

export interface ChatEvent {
  type: ChatEventType
  conversationId: string
  content: string
}

export interface ChatRequest {
  conversationId: string | null
  message: string
  mode: AgentMode
  userMessageId: string | null
  userParentMessageId: string | null
  assistantMessageId: string | null
}

export interface RuntimeConfig {
  provider: string
  fastModel: string
  deepModel: string
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

export async function* streamChatEvents(
  request: ChatRequest,
  signal?: AbortSignal,
): AsyncGenerator<ChatEvent> {
  const response = await fetch(`${apiBaseUrl}/api/chat/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
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
