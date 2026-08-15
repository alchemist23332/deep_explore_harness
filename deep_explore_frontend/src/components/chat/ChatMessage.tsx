import {
  ActionBarPrimitive,
  AuiIf,
  MessagePrimitive,
  useAuiState,
} from '@assistant-ui/react'
import * as Tooltip from '@radix-ui/react-tooltip'
import {
  Bot,
  Check,
  Copy,
  RefreshCw,
  TriangleAlert,
  UserRound,
} from 'lucide-react'
import { lazy, Suspense, type ReactElement } from 'react'

const MarkdownText = lazy(() =>
  import('./MarkdownText').then((module) => ({
    default: module.MarkdownText,
  })),
)

export function UserMessage() {
  return (
    <MessagePrimitive.Root className="message-row user-message">
      <div className="message-avatar user-avatar" aria-hidden="true">
        <UserRound size={16} />
      </div>
      <div className="user-message-content">
        <MessagePrimitive.Parts components={{ Text: UserText }} />
      </div>
    </MessagePrimitive.Root>
  )
}

export function AssistantMessage() {
  const isRunning = useAuiState(
    (state) => state.message.status?.type === 'running',
  )

  return (
    <MessagePrimitive.Root className="message-row assistant-message">
      <div className="message-avatar assistant-avatar" aria-hidden="true">
        <Bot size={17} />
      </div>
      <div className="assistant-message-body">
        <div className="assistant-message-content">
          <MessagePrimitive.Parts components={{ Text: AssistantText }} />
          {isRunning && <GeneratingState />}
          <MessagePrimitive.Error>
            <div className="message-error" role="alert">
              <TriangleAlert size={16} />
              <span>生成失败，请检查服务状态后重试。</span>
            </div>
          </MessagePrimitive.Error>
        </div>

        <ActionBarPrimitive.Root className="message-actions">
          <ActionTooltip label="复制回答">
            <ActionBarPrimitive.Copy className="icon-button message-action">
              <AuiIf condition={(state) => !state.message.isCopied}>
                <Copy size={15} />
              </AuiIf>
              <AuiIf condition={(state) => state.message.isCopied}>
                <Check size={15} />
              </AuiIf>
            </ActionBarPrimitive.Copy>
          </ActionTooltip>
          <ActionTooltip label="重新生成">
            <ActionBarPrimitive.Reload className="icon-button message-action">
              <RefreshCw size={15} />
            </ActionBarPrimitive.Reload>
          </ActionTooltip>
        </ActionBarPrimitive.Root>
      </div>
    </MessagePrimitive.Root>
  )
}

function UserText() {
  const text = useAuiState((state) =>
    state.part.type === 'text' ? state.part.text : '',
  )
  return <div className="user-text">{text}</div>
}

function AssistantText() {
  return (
    <Suspense fallback={<div className="markdown-loading">正在格式化回答...</div>}>
      <MarkdownText />
    </Suspense>
  )
}

function GeneratingState() {
  const hasText = useAuiState((state) =>
    state.message.parts.some(
      (part) => part.type === 'text' && part.text.length > 0,
    ),
  )

  if (hasText) {
    return <span className="streaming-caret" aria-label="正在生成" />
  }

  return (
    <div className="generating-state" aria-label="正在生成回答">
      <span />
      <span />
      <span />
    </div>
  )
}

function ActionTooltip({
  label,
  children,
}: {
  label: string
  children: ReactElement
}) {
  return (
    <Tooltip.Root>
      <Tooltip.Trigger asChild>{children}</Tooltip.Trigger>
      <Tooltip.Portal>
        <Tooltip.Content className="tooltip-content" sideOffset={6}>
          {label}
          <Tooltip.Arrow className="tooltip-arrow" />
        </Tooltip.Content>
      </Tooltip.Portal>
    </Tooltip.Root>
  )
}
