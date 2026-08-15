import { useAuiState } from '@assistant-ui/react'
import { BrainCircuit, Menu, Moon, Sun, Zap } from 'lucide-react'
import { useWorkspace } from '../../runtime/workspace-context'

export function AppHeader({ onOpenSidebar }: { onOpenSidebar: () => void }) {
  const title = useAuiState(
    (state) => state.threadListItem.title ?? '新对话',
  )
  const {
    mode,
    config,
    serviceAvailable,
    theme,
    toggleTheme,
  } = useWorkspace()
  const model = mode === 'FAST' ? config?.fastModel : config?.deepModel

  return (
    <header className="workspace-header">
      <div className="header-primary">
        <button
          type="button"
          className="icon-button mobile-menu-button"
          onClick={onOpenSidebar}
          aria-label="打开会话列表"
          title="打开会话列表"
        >
          <Menu size={19} />
        </button>
        <div className="conversation-heading">
          <h1>{title}</h1>
          <div className="model-status">
            <span
              className={serviceAvailable ? 'status-dot online' : 'status-dot'}
            />
            {mode === 'FAST' ? <Zap size={13} /> : <BrainCircuit size={13} />}
            <span>{model ?? '模型配置读取中'}</span>
          </div>
        </div>
      </div>

      <button
        type="button"
        className="icon-button"
        onClick={toggleTheme}
        aria-label={theme === 'light' ? '切换到深色主题' : '切换到浅色主题'}
        title={theme === 'light' ? '深色主题' : '浅色主题'}
      >
        {theme === 'light' ? <Moon size={18} /> : <Sun size={18} />}
      </button>
    </header>
  )
}
