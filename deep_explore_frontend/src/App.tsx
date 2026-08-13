import { useEffect, useRef, useState } from 'react'
import type { KeyboardEvent, SubmitEvent } from 'react'
import { streamChat } from './api/chat'
import type { AgentMode, ChatEvent } from './api/chat'
import './App.css'

type MessageRole = 'user' | 'assistant'
type MessageStatus = 'streaming' | 'done' | 'error'

interface Message {
  id: string
  role: MessageRole
  content: string
  status: MessageStatus
}

const welcomeMessage: Message = {
  id: 'welcome',
  role: 'assistant',
  content: '你好，我是 Deep Explore。你可以从一个问题开始。',
  status: 'done',
}

function App() {
  const [messages, setMessages] = useState<Message[]>([welcomeMessage])
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [input, setInput] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [mode, setMode] = useState<AgentMode>('FAST')
  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const submitMessage = async (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault()
    const message = input.trim()
    if (!message || isStreaming) {
      return
    }

    const assistantMessageId = crypto.randomUUID()
    setInput('')
    setIsStreaming(true)
    setMessages((current) => [
      ...current,
      {
        id: crypto.randomUUID(),
        role: 'user',
        content: message,
        status: 'done',
      },
      {
        id: assistantMessageId,
        role: 'assistant',
        content: '',
        status: 'streaming',
      },
    ])

    try {
      await streamChat(
        { conversationId, message, mode },
        (chatEvent) => handleChatEvent(chatEvent, assistantMessageId),
      )
    } catch (error) {
      const content =
        error instanceof Error ? error.message : '连接后端服务失败，请稍后重试'
      updateAssistantMessage(assistantMessageId, content, 'error')
    } finally {
      setIsStreaming(false)
    }
  }

  const handleChatEvent = (event: ChatEvent, assistantMessageId: string) => {
    if (event.conversationId) {
      setConversationId(event.conversationId)
    }

    if (event.type === 'delta') {
      setMessages((current) =>
        current.map((message) =>
          message.id === assistantMessageId
            ? { ...message, content: message.content + event.content }
            : message,
        ),
      )
    } else if (event.type === 'done') {
      updateAssistantMessage(assistantMessageId, undefined, 'done')
    } else if (event.type === 'error') {
      updateAssistantMessage(assistantMessageId, event.content, 'error')
    }
  }

  const updateAssistantMessage = (
    id: string,
    content: string | undefined,
    status: MessageStatus,
  ) => {
    setMessages((current) =>
      current.map((message) =>
        message.id === id
          ? {
              ...message,
              content: content ?? message.content,
              status,
            }
          : message,
      ),
    )
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (
      event.key === 'Enter' &&
      !event.shiftKey &&
      !event.nativeEvent.isComposing
    ) {
      event.preventDefault()
      event.currentTarget.form?.requestSubmit()
    }
  }

  const startNewConversation = () => {
    if (!isStreaming) {
      setConversationId(null)
      setMessages([welcomeMessage])
      setInput('')
    }
  }

  return (
    <main className="app-shell">
      <header className="app-header">
        <div>
          <p className="eyebrow">AI AGENT PLAYGROUND</p>
          <h1>Deep Explore</h1>
        </div>
        <button
          className="secondary-button"
          type="button"
          onClick={startNewConversation}
          disabled={isStreaming}
        >
          新对话
        </button>
      </header>

      <section className="chat-panel" aria-label="聊天消息">
        <div className="message-list" aria-live="polite">
          {messages.map((message) => (
            <article
              className={`message message-${message.role}`}
              key={message.id}
            >
              <div className="message-label">
                {message.role === 'user' ? '你' : 'AI'}
              </div>
              <div className={`message-bubble status-${message.status}`}>
                {message.content || (
                  <span className="typing-indicator">正在思考...</span>
                )}
              </div>
            </article>
          ))}
          <div ref={messagesEndRef} />
        </div>

        <form className="chat-form" onSubmit={submitMessage}>
          <textarea
            value={input}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入消息，Enter 发送，Shift + Enter 换行"
            rows={3}
            maxLength={20_000}
            disabled={isStreaming}
            aria-label="聊天输入"
          />
          <div className="form-footer">
            <div className="form-meta">
              <span className="connection-status">
                {conversationId ? '会话已建立' : '等待新会话'}
              </span>
              <div className="mode-switch" aria-label="回答模式">
                <button
                  className={mode === 'FAST' ? 'mode-button active' : 'mode-button'}
                  type="button"
                  onClick={() => setMode('FAST')}
                  disabled={isStreaming}
                  title="关闭思考，适合普通问题"
                >
                  快速
                </button>
                <button
                  className={mode === 'DEEP' ? 'mode-button active' : 'mode-button'}
                  type="button"
                  onClick={() => setMode('DEEP')}
                  disabled={isStreaming}
                  title="低强度思考，适合复杂问题"
                >
                  深度
                </button>
              </div>
            </div>
            <button
              className="send-button"
              type="submit"
              disabled={!input.trim() || isStreaming}
            >
              {isStreaming ? '生成中' : '发送'}
            </button>
          </div>
        </form>
      </section>
    </main>
  )
}

export default App
