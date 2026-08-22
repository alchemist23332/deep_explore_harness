import {
  RuntimeAdapterProvider,
  useAui,
  type RemoteThreadListAdapter,
  type ThreadHistoryAdapter,
} from '@assistant-ui/react'
import { createAssistantStream } from 'assistant-stream'
import { createElement, useMemo, useRef, type PropsWithChildren } from 'react'
import { createStore, get } from 'idb-keyval'
import {
  createConversation,
  deleteConversation,
  getConversation,
  getConversationMessages,
  importConversations,
  listConversations,
  updateConversation,
  type ImportedConversation,
} from '../api/conversations'
import { getRunActivities } from '../api/chat'
import type { RunActivityData } from '../types/tool-activity'

const indexedDb = createStore('deep-explore', 'assistant-ui')
const migrationMarker = 'deep-explore:server-migration:v1'
const messageConversationIds = new Map<string, string>()
let pendingInitializedConversationId: string | null = null

export function resolveConversationId(
  messageId: string | undefined,
  runtimeConversationId: string | undefined,
) {
  if (runtimeConversationId) {
    if (messageId) messageConversationIds.delete(messageId)
    return runtimeConversationId
  }
  if (!messageId) return null
  const conversationId = messageConversationIds.get(messageId) ?? null
  messageConversationIds.delete(messageId)
  return conversationId
}

class ServerHistoryAdapter implements ThreadHistoryAdapter {
  private readonly getAui: () => ReturnType<typeof useAui>

  constructor(getAui: () => ReturnType<typeof useAui>) {
    this.getAui = getAui
  }

  async load() {
    const remoteId = this.getAui().threadListItem.getState().remoteId
    if (!remoteId) return { messages: [] }

    const [repository, runActivities] = await Promise.all([
      getConversationMessages(remoteId),
      getRunActivities(remoteId).catch(() => []),
    ])
    const activityByMessageId = new Map<string, RunActivityData>()
    for (const activity of runActivities) {
      if (activity.assistantMessageId) {
        activityByMessageId.set(activity.assistantMessageId, activity)
      }
    }
    return {
      headId: repository.headId,
      messages: repository.messages.map((stored) => ({
        parentId: stored.parentMessageId,
        message:
          stored.role === 'USER'
            ? {
                id: stored.id,
                role: 'user' as const,
                content: [{ type: 'text' as const, text: stored.content }],
                attachments: [],
                createdAt: new Date(stored.createdAt),
                metadata: { custom: {} },
              }
            : {
                id: stored.id,
                role: 'assistant' as const,
                content: [
                  ...(activityByMessageId.has(stored.id)
                    ? [
                        {
                          type: 'data' as const,
                          name: 'tool-activity',
                          data: activityByMessageId.get(stored.id)!,
                        },
                      ]
                    : []),
                  { type: 'text' as const, text: stored.content },
                ],
                status:
                  stored.status === 'COMPLETE'
                    ? ({ type: 'complete', reason: 'unknown' } as const)
                    : ({ type: 'incomplete', reason: 'error' } as const),
                createdAt: new Date(stored.createdAt),
                metadata: {
                  unstable_state: null,
                  unstable_annotations: [],
                  unstable_data: [],
                  steps: [],
                  custom: {},
                },
              },
      })),
    }
  }

  async append(item: Parameters<ThreadHistoryAdapter['append']>[0]) {
    if (item.message.role === 'user') {
      const remoteId =
        this.getAui().threadListItem.getState().remoteId ??
        pendingInitializedConversationId
      if (remoteId) {
        messageConversationIds.set(item.message.id, remoteId)
        pendingInitializedConversationId = null
      }
    }
    // The chat endpoint persists user and assistant messages atomically.
  }

  async update() {
    // Terminal assistant state is persisted by the chat endpoint.
  }
}

function ServerHistoryProvider({ children }: PropsWithChildren) {
  const aui = useAui()
  const auiRef = useRef(aui)
  auiRef.current = aui
  const history = useMemo(
    () => new ServerHistoryAdapter(() => auiRef.current),
    [],
  )

  const providerProps = { adapters: { history }, children }
  return createElement(RuntimeAdapterProvider, providerProps)
}

export const conversationAdapter: RemoteThreadListAdapter = {
  unstable_Provider: ServerHistoryProvider,

  async list() {
    await migrateIndexedDbHistory()
    const conversations = await listConversations()
    return {
      threads: conversations.map((conversation) => ({
        remoteId: conversation.id,
        status:
          conversation.status === 'ARCHIVED'
            ? ('archived' as const)
            : ('regular' as const),
        title: conversation.title ?? undefined,
        lastMessageAt: new Date(conversation.updatedAt),
      })),
    }
  },

  async initialize() {
    const conversation = await createConversation()
    pendingInitializedConversationId = conversation.id
    return { remoteId: conversation.id }
  },

  async rename(remoteId, newTitle) {
    await updateConversation(remoteId, { title: newTitle })
  },

  async archive(remoteId) {
    await updateConversation(remoteId, { status: 'ARCHIVED' })
  },

  async unarchive(remoteId) {
    await updateConversation(remoteId, { status: 'REGULAR' })
  },

  async delete(remoteId) {
    await deleteConversation(remoteId)
  },

  async fetch(threadId) {
    const conversation = await getConversation(threadId)
    return {
      remoteId: conversation.id,
      status:
        conversation.status === 'ARCHIVED' ? 'archived' : 'regular',
      title: conversation.title ?? undefined,
      lastMessageAt: new Date(conversation.updatedAt),
    }
  },

  async generateTitle(remoteId) {
    const conversation = await getConversation(remoteId)
    return createAssistantStream((controller) => {
      if (conversation.title) {
        controller.appendText(conversation.title)
      }
    })
  },
}

async function migrateIndexedDbHistory() {
  if (window.localStorage.getItem(migrationMarker) === 'done') return

  const rawThreads = await get<string>(
    'deep-explore:threads',
    indexedDb,
  )
  const parsedThreads = parseRecordArray(rawThreads)
  const conversations: ImportedConversation[] = []

  for (const thread of parsedThreads) {
    if (typeof thread.remoteId !== 'string') continue
    const rawMessages = await get<string>(
      `deep-explore:messages:${thread.remoteId}`,
      indexedDb,
    )
    const repository = parseObject(rawMessages)
    const rawItems = Array.isArray(repository?.messages)
      ? repository.messages
      : []
    const messages: ImportedConversation['messages'] = []

    for (const candidate of rawItems) {
      const item = isRecord(candidate) ? candidate : null
      const message = isRecord(item?.message) ? item.message : null
      if (!message || typeof message.id !== 'string') continue
      if (message.role !== 'user' && message.role !== 'assistant') continue

      const content = Array.isArray(message.content)
        ? message.content
            .filter(
              (part): part is Record<string, unknown> =>
                isRecord(part) &&
                part.type === 'text' &&
                typeof part.text === 'string',
            )
            .map((part) => String(part.text))
            .join('\n')
            .trim()
        : ''
      if (!content) continue

      const status = isRecord(message.status)
        ? message.status.type
        : 'complete'
      messages.push({
        id: message.id,
        parentMessageId:
          typeof item?.parentId === 'string' ? item.parentId : null,
        role: message.role === 'user' ? 'USER' : 'ASSISTANT',
        content,
        status: status === 'complete' ? 'COMPLETE' : 'INCOMPLETE',
        createdAt:
          typeof message.createdAt === 'string'
            ? message.createdAt
            : new Date().toISOString(),
      })
    }

    conversations.push({
      id: thread.remoteId,
      ...(typeof thread.title === 'string' ? { title: thread.title } : {}),
      status: thread.status === 'archived' ? 'ARCHIVED' : 'REGULAR',
      messages,
    })
  }

  await importConversations(conversations)
  window.localStorage.setItem(migrationMarker, 'done')
}

function parseRecordArray(raw?: string): Record<string, unknown>[] {
  const parsed = parseJson(raw)
  return Array.isArray(parsed) ? parsed.filter(isRecord) : []
}

function parseObject(raw?: string): Record<string, unknown> | null {
  const parsed = parseJson(raw)
  return isRecord(parsed) ? parsed : null
}

function parseJson(raw?: string): unknown {
  if (!raw) return null
  try {
    return JSON.parse(raw) as unknown
  } catch {
    return null
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
