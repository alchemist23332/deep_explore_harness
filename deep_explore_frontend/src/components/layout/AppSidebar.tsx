import {
  ThreadListItemPrimitive,
  ThreadListPrimitive,
  useAui,
  useAuiState,
} from '@assistant-ui/react'
import * as Dialog from '@radix-ui/react-dialog'
import {
  Bot,
  MessageSquareText,
  MoreHorizontal,
  Pencil,
  Plus,
  Search,
  Trash2,
} from 'lucide-react'
import { useState } from 'react'
import { useWorkspace } from '../../runtime/workspace-context'

export function AppSidebar({ onNavigate }: { onNavigate?: () => void }) {
  const [search, setSearch] = useState('')
  const { config, serviceAvailable } = useWorkspace()

  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brand-mark" aria-hidden="true">
          <Bot size={20} />
        </div>
        <div>
          <strong>Deep Explore</strong>
          <span>AI Agent Workspace</span>
        </div>
      </div>

      <ThreadListPrimitive.New
        className="new-thread-button"
        onClick={onNavigate}
      >
        <Plus size={17} />
        新建对话
      </ThreadListPrimitive.New>

      <label className="thread-search">
        <Search size={15} aria-hidden="true" />
        <input
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="搜索会话"
          aria-label="搜索会话"
        />
      </label>

      <div className="thread-section">
        <div className="thread-section-title">最近对话</div>
        <ThreadListPrimitive.Root className="thread-list">
          <ThreadListPrimitive.Items>
            {({ threadListItem }) => {
              const title = threadListItem.title ?? '新对话'
              if (
                search &&
                !title.toLocaleLowerCase().includes(search.toLocaleLowerCase())
              ) {
                return null
              }
              return <ThreadItem onNavigate={onNavigate} />
            }}
          </ThreadListPrimitive.Items>
        </ThreadListPrimitive.Root>
      </div>

      <div className="sidebar-footer">
        <div className="service-row">
          <span
            className={serviceAvailable ? 'status-dot online' : 'status-dot'}
          />
          <div>
            <strong>{serviceAvailable ? '服务正常' : '服务离线'}</strong>
            <small>{formatProvider(config?.provider)}</small>
          </div>
        </div>
        <div className="sidebar-model" title={config?.fastModel}>
          {config?.fastModel ?? '正在读取模型配置'}
        </div>
      </div>
    </aside>
  )
}

function ThreadItem({ onNavigate }: { onNavigate?: () => void }) {
  const aui = useAui()
  const title = useAuiState(
    (state) => state.threadListItem.title ?? '新对话',
  )
  const [manageOpen, setManageOpen] = useState(false)
  const [nextTitle, setNextTitle] = useState(title)

  const rename = () => {
    const value = nextTitle.trim()
    if (value) {
      aui.threadListItem.rename(value)
      setManageOpen(false)
    }
  }

  return (
    <>
      <ThreadListItemPrimitive.Root className="thread-item">
        <ThreadListItemPrimitive.Trigger
          className="thread-trigger"
          onClick={onNavigate}
        >
          <MessageSquareText size={15} />
          <span>
            <ThreadListItemPrimitive.Title fallback="新对话" />
          </span>
        </ThreadListItemPrimitive.Trigger>

        <button
          type="button"
          className="thread-more"
          aria-label={`管理会话：${title}`}
          onClick={() => {
            setNextTitle(title)
            setManageOpen(true)
          }}
        >
          <MoreHorizontal size={16} />
        </button>
      </ThreadListItemPrimitive.Root>

      <Dialog.Root open={manageOpen} onOpenChange={setManageOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="dialog-overlay" />
          <Dialog.Content className="dialog-content">
            <Dialog.Title>管理会话</Dialog.Title>
            <Dialog.Description>
              修改会话名称，或删除本地保存的完整消息记录。
            </Dialog.Description>
            <input
              className="dialog-input"
              value={nextTitle}
              onChange={(event) => setNextTitle(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') rename()
              }}
              autoFocus
              maxLength={80}
            />
            <div className="dialog-actions">
              <button
                type="button"
                className="button danger manage-delete"
                onClick={() => {
                  aui.threadListItem.delete()
                  setManageOpen(false)
                }}
              >
                <Trash2 size={14} />
                删除
              </button>
              <div className="dialog-action-spacer" />
              <Dialog.Close asChild>
                <button type="button" className="button secondary">
                  取消
                </button>
              </Dialog.Close>
              <button type="button" className="button primary" onClick={rename}>
                <Pencil size={14} />
                保存
              </button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </>
  )
}

function formatProvider(provider?: string) {
  if (!provider) return '连接检测中'
  if (provider === 'DEEPSEEK') return 'DeepSeek API'
  if (provider === 'OLLAMA') return 'Ollama 本地模型'
  return 'OpenAI Compatible'
}
