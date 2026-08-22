import {
  AssistantRuntimeProvider,
  useLocalRuntime,
  useRemoteThreadListRuntime,
  type ChatModelAdapter,
  type ThreadAssistantMessagePart,
} from '@assistant-ui/react'
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren,
} from 'react'
import {
  checkHealth,
  getRuntimeConfig,
  streamChatEvents,
  type AgentMode,
  type RuntimeConfig,
  type SearchProvider,
} from '../api/chat'
import { conversationAdapter } from '../storage/conversations'
import {
  WorkspaceContext,
  type Theme,
  type WorkspaceContextValue,
} from './workspace-context'
import { resolveConversationId } from '../storage/conversations'
import type {
  RunActivityData,
  RunActivityStatus,
  ToolActivity,
  ToolActivityStatus,
} from '../types/tool-activity'

export function DeepExploreRuntimeProvider({ children }: PropsWithChildren) {
  const [mode, setMode] = useState<AgentMode>('FAST')
  const [searchProvider, setSearchProvider] =
    useState<SearchProvider>('TAVILY')
  const [config, setConfig] = useState<RuntimeConfig | null>(null)
  const [serviceAvailable, setServiceAvailable] = useState(false)
  const [theme, setTheme] = useState<Theme>(() => {
    const saved = window.localStorage.getItem('deep-explore:theme')
    if (saved === 'light' || saved === 'dark') return saved
    return window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light'
  })
  const modeRef = useRef(mode)
  modeRef.current = mode
  const searchProviderRef = useRef(searchProvider)
  const searchProviderInitialized = useRef(false)
  searchProviderRef.current = searchProvider

  const chatModel = useMemo<ChatModelAdapter>(
    () => ({
      async *run({
        messages,
        abortSignal,
        unstable_threadId,
        unstable_assistantMessageId,
      }) {
        const latestUserIndex = messages.findLastIndex(
          (message) => message.role === 'user',
        )
        const latestUserMessage = messages[latestUserIndex]
        const input =
          latestUserMessage?.content
            .filter((part) => part.type === 'text')
            .map((part) => part.text)
            .join('\n')
            .trim() ?? ''

        if (!input) {
          throw new Error('消息内容不能为空')
        }

        const conversationId = resolveConversationId(
          latestUserMessage?.id,
          unstable_threadId,
        )
        let text = ''
        let activity: RunActivityData | null = null

        try {
          for await (const event of streamChatEvents(
            {
              conversationId,
              message: input,
              mode: modeRef.current,
              userMessageId: latestUserMessage?.id ?? null,
              userParentMessageId:
                latestUserIndex > 0
                  ? messages[latestUserIndex - 1]?.id ?? null
                  : null,
              assistantMessageId: unstable_assistantMessageId ?? null,
              searchProvider: searchProviderRef.current,
            },
            abortSignal,
          )) {
            if (event.type === 'metadata') {
              activity = {
                runId:
                  event.runId ??
                  unstable_assistantMessageId ??
                  'pending-run',
                assistantMessageId:
                  event.assistantMessageId ??
                  unstable_assistantMessageId ??
                  null,
                status: 'RUNNING',
                tools: [],
              }
            } else if (event.type === 'tool_start' && event.tool) {
              activity = upsertToolActivity(
                ensureRunActivity(
                  activity,
                  event.runId,
                  event.assistantMessageId ??
                    unstable_assistantMessageId ??
                    null,
                ),
                {
                  ...event.tool,
                  startedAt: event.occurredAt,
                  completedAt: null,
                  durationMs: null,
                },
              )
              yield { content: messageContent(text, activity) }
            } else if (event.type === 'tool_end' && event.tool) {
              activity = completeToolActivity(
                ensureRunActivity(
                  activity,
                  event.runId,
                  event.assistantMessageId ??
                    unstable_assistantMessageId ??
                    null,
                ),
                event.tool,
                event.occurredAt,
              )
              yield { content: messageContent(text, activity) }
            } else if (event.type === 'delta') {
              text += event.content
              yield {
                content: messageContent(text, activity),
              }
            } else if (event.type === 'done') {
              activity = finishRunActivity(activity, 'COMPLETED')
              if (activity?.tools.length) {
                yield { content: messageContent(text, activity) }
              }
            } else if (event.type === 'error') {
              activity = finishRunActivity(activity, 'FAILED')
              if (activity?.tools.length) {
                yield { content: messageContent(text, activity) }
              }
              throw new Error(event.content)
            }
          }
        } catch (error) {
          if (abortSignal.aborted && activity?.tools.length) {
            activity = finishRunActivity(activity, 'CANCELLED')
            yield { content: messageContent(text, activity) }
          }
          throw error
        }
      },
    }),
    [],
  )

  const runtime = useRemoteThreadListRuntime({
    runtimeHook: function RuntimeHook() {
      return useLocalRuntime(chatModel)
    },
    adapter: conversationAdapter,
  })

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    window.localStorage.setItem('deep-explore:theme', theme)
  }, [theme])

  useEffect(() => {
    let active = true

    const refresh = async () => {
      const [nextConfig, available] = await Promise.all([
        getRuntimeConfig().catch(() => null),
        checkHealth(),
      ])
      if (!active) return
      if (nextConfig && nextConfig.availableSearchProviders.length > 0) {
        const available = nextConfig.availableSearchProviders
        const nextProvider = searchProviderInitialized.current
          ? searchProviderRef.current
          : nextConfig.defaultSearchProvider
        const resolvedProvider = available.includes(nextProvider)
          ? nextProvider
          : available[0]
        if (resolvedProvider !== searchProviderRef.current) {
          searchProviderRef.current = resolvedProvider
          setSearchProvider(resolvedProvider)
        }
        searchProviderInitialized.current = true
      }
      setConfig(nextConfig)
      setServiceAvailable(available)
    }

    void refresh()
    const timer = window.setInterval(refresh, 15_000)
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [])

  const workspace = useMemo<WorkspaceContextValue>(
    () => ({
      mode,
      setMode,
      searchProvider,
      setSearchProvider,
      config,
      serviceAvailable,
      theme,
      toggleTheme: () =>
        setTheme((current) => (current === 'light' ? 'dark' : 'light')),
    }),
    [config, mode, searchProvider, serviceAvailable, theme],
  )

  return (
    <WorkspaceContext.Provider value={workspace}>
      <AssistantRuntimeProvider runtime={runtime}>
        {children}
      </AssistantRuntimeProvider>
    </WorkspaceContext.Provider>
  )
}

function ensureRunActivity(
  activity: RunActivityData | null,
  runId: string | null,
  assistantMessageId: string | null,
): RunActivityData {
  return (
    activity ?? {
      runId: runId ?? assistantMessageId ?? 'pending-run',
      assistantMessageId,
      status: 'RUNNING',
      tools: [],
    }
  )
}

function upsertToolActivity(
  activity: RunActivityData,
  tool: ToolActivity,
): RunActivityData {
  const index = activity.tools.findIndex(
    (candidate) => candidate.toolCallId === tool.toolCallId,
  )
  const tools =
    index < 0
      ? [...activity.tools, tool]
      : activity.tools.map((candidate, candidateIndex) =>
          candidateIndex === index ? tool : candidate,
        )
  return { ...activity, status: 'RUNNING', tools }
}

function completeToolActivity(
  activity: RunActivityData,
  tool: {
    toolCallId: string
    toolName: string
    displayName: string
    status: ToolActivityStatus
    summary: string
    provider: string | null
  },
  completedAt: string | null,
): RunActivityData {
  const existing = activity.tools.find(
    (candidate) => candidate.toolCallId === tool.toolCallId,
  )
  const completed: ToolActivity = {
    ...tool,
    startedAt: existing?.startedAt ?? null,
    completedAt,
    durationMs: durationBetween(existing?.startedAt ?? null, completedAt),
  }
  return upsertToolActivity(activity, completed)
}

function finishRunActivity(
  activity: RunActivityData | null,
  status: RunActivityStatus,
): RunActivityData | null {
  if (!activity) return null
  const toolStatus: ToolActivityStatus =
    status === 'CANCELLED' ? 'CANCELLED' : 'FAILED'
  return {
    ...activity,
    status,
    tools: activity.tools.map((tool) =>
      tool.status === 'RUNNING' && status !== 'COMPLETED'
        ? {
            ...tool,
            status: toolStatus,
            summary:
              status === 'CANCELLED'
                ? `${tool.displayName}已取消`
                : `${tool.displayName}执行中断`,
          }
        : tool,
    ),
  }
}

function messageContent(
  text: string,
  activity: RunActivityData | null,
): ThreadAssistantMessagePart[] {
  const content: ThreadAssistantMessagePart[] = []
  if (activity?.tools.length) {
    content.push({
      type: 'data',
      name: 'tool-activity',
      data: activity,
    })
  }
  if (text) {
    content.push({ type: 'text', text })
  }
  return content
}

function durationBetween(
  startedAt: string | null,
  completedAt: string | null,
): number | null {
  if (!startedAt || !completedAt) return null
  const duration = Date.parse(completedAt) - Date.parse(startedAt)
  return Number.isFinite(duration) ? Math.max(0, duration) : null
}
