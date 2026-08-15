export type ConversationStatus = 'REGULAR' | 'ARCHIVED'

export interface ConversationSummary {
  id: string
  title: string | null
  status: ConversationStatus
  headMessageId: string | null
  createdAt: string
  updatedAt: string
}

export interface ConversationMessage {
  id: string
  parentMessageId: string | null
  role: 'USER' | 'ASSISTANT'
  content: string
  status: 'COMPLETE' | 'INCOMPLETE'
  createdAt: string
}

export interface ConversationMessages {
  headId: string | null
  messages: ConversationMessage[]
}

export interface ImportedConversation {
  id: string
  title?: string
  status?: ConversationStatus
  messages: Array<{
    id: string
    parentMessageId: string | null
    role: 'USER' | 'ASSISTANT'
    content: string
    status: 'COMPLETE' | 'INCOMPLETE'
    createdAt: string
  }>
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

export async function listConversations(): Promise<ConversationSummary[]> {
  return requestJson('/api/conversations')
}

export async function createConversation(): Promise<ConversationSummary> {
  return requestJson('/api/conversations', { method: 'POST' })
}

export async function getConversation(
  conversationId: string,
): Promise<ConversationSummary> {
  return requestJson(`/api/conversations/${conversationId}`)
}

export async function updateConversation(
  conversationId: string,
  patch: { title?: string; status?: ConversationStatus },
): Promise<ConversationSummary> {
  return requestJson(`/api/conversations/${conversationId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(patch),
  })
}

export async function deleteConversation(
  conversationId: string,
): Promise<void> {
  await request(`/api/conversations/${conversationId}`, { method: 'DELETE' })
}

export async function getConversationMessages(
  conversationId: string,
): Promise<ConversationMessages> {
  return requestJson(`/api/conversations/${conversationId}/messages`)
}

export async function importConversations(
  conversations: ImportedConversation[],
): Promise<void> {
  if (conversations.length === 0) return
  await request('/api/conversations/import', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversations }),
  })
}

async function requestJson<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const response = await request(path, init)
  return response.json() as Promise<T>
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(`${apiBaseUrl}${path}`, init)
  if (!response.ok) {
    const body = await response.text()
    throw new Error(body || `请求失败（HTTP ${response.status}）`)
  }
  return response
}
