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
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

export async function streamChat(
  request: ChatRequest,
  onEvent: (event: ChatEvent) => void,
): Promise<void> {
  const response = await fetch(`${apiBaseUrl}/api/chat/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw new Error(`请求失败（HTTP ${response.status}）`)
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
      emitEvent(block, onEvent)
    }

    if (done) {
      if (buffer.trim()) {
        emitEvent(buffer, onEvent)
      }
      break
    }
  }
}

function emitEvent(block: string, onEvent: (event: ChatEvent) => void) {
  const data = block
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  if (data) {
    onEvent(JSON.parse(data) as ChatEvent)
  }
}
