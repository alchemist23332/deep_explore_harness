import {
  AssistantRuntimeProvider,
  useLocalRuntime,
  useRemoteThreadListRuntime,
  type ChatModelAdapter,
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
} from '../api/chat'
import { conversationAdapter } from '../storage/conversations'
import {
  WorkspaceContext,
  type Theme,
  type WorkspaceContextValue,
} from './workspace-context'
import { resolveConversationId } from '../storage/conversations'

export function DeepExploreRuntimeProvider({ children }: PropsWithChildren) {
  const [mode, setMode] = useState<AgentMode>('FAST')
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

        let text = ''
        for await (const event of streamChatEvents(
          {
            conversationId: resolveConversationId(
              latestUserMessage?.id,
              unstable_threadId,
            ),
            message: input,
            mode: modeRef.current,
            userMessageId: latestUserMessage?.id ?? null,
            userParentMessageId:
              latestUserIndex > 0 ? messages[latestUserIndex - 1]?.id ?? null : null,
            assistantMessageId: unstable_assistantMessageId ?? null,
          },
          abortSignal,
        )) {
          if (event.type === 'delta') {
            text += event.content
            yield {
              content: [{ type: 'text', text }],
            }
          } else if (event.type === 'error') {
            throw new Error(event.content)
          }
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
      config,
      serviceAvailable,
      theme,
      toggleTheme: () =>
        setTheme((current) => (current === 'light' ? 'dark' : 'light')),
    }),
    [config, mode, serviceAvailable, theme],
  )

  return (
    <WorkspaceContext.Provider value={workspace}>
      <AssistantRuntimeProvider runtime={runtime}>
        {children}
      </AssistantRuntimeProvider>
    </WorkspaceContext.Provider>
  )
}
