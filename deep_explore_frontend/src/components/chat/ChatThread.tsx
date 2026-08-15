import { ThreadPrimitive } from '@assistant-ui/react'
import { ArrowDown } from 'lucide-react'
import { AssistantMessage, UserMessage } from './ChatMessage'
import { ChatComposer } from './ChatComposer'
import { EmptyState } from './EmptyState'

export function ChatThread() {
  return (
    <ThreadPrimitive.Root className="thread-root">
      <ThreadPrimitive.Viewport className="thread-viewport">
        <div className="thread-content">
          <EmptyState />
          <ThreadPrimitive.Messages
            components={{
              UserMessage,
              AssistantMessage,
            }}
          />
        </div>

        <ThreadPrimitive.ScrollToBottom
          className="scroll-to-bottom"
          aria-label="回到底部"
          title="回到底部"
        >
          <ArrowDown size={17} />
        </ThreadPrimitive.ScrollToBottom>
      </ThreadPrimitive.Viewport>

      <ChatComposer />
    </ThreadPrimitive.Root>
  )
}
