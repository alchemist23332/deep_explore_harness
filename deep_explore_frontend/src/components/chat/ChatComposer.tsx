import {
  AuiIf,
  ComposerPrimitive,
  useAuiState,
} from '@assistant-ui/react'
import { SendHorizontal, Square } from 'lucide-react'
import { useWorkspace } from '../../runtime/workspace-context'

export function ChatComposer() {
  const { mode, setMode, config } = useWorkspace()
  const isRunning = useAuiState((state) => state.thread.isRunning)
  const inputLength = useAuiState(
    (state) => state.thread.composer.text.length,
  )
  const model = mode === 'FAST' ? config?.fastModel : config?.deepModel

  return (
    <div className="composer-region">
      <ComposerPrimitive.Root className="composer">
        <ComposerPrimitive.Input
          className="composer-input"
          maxLength={20_000}
          placeholder="输入问题，Enter 发送，Shift + Enter 换行"
          rows={1}
        />

        <div className="composer-toolbar">
          <div className="composer-options">
            <div className="mode-selector" aria-label="回答模式">
              <button
                type="button"
                className={mode === 'FAST' ? 'active' : undefined}
                onClick={() => setMode('FAST')}
                disabled={isRunning}
              >
                快速
              </button>
              <button
                type="button"
                className={mode === 'DEEP' ? 'active' : undefined}
                onClick={() => setMode('DEEP')}
                disabled={isRunning}
              >
                深度
              </button>
            </div>
            <span className="active-model" title={model ?? undefined}>
              {model ?? '正在读取模型'}
            </span>
          </div>

          <div className="composer-actions">
            <span className="character-count">{inputLength.toLocaleString()}</span>
            <AuiIf condition={(state) => !state.thread.isRunning}>
              <ComposerPrimitive.Send
                className="send-button"
                aria-label="发送消息"
                title="发送消息"
              >
                <SendHorizontal size={17} />
              </ComposerPrimitive.Send>
            </AuiIf>
            <AuiIf condition={(state) => state.thread.isRunning}>
              <ComposerPrimitive.Cancel
                className="stop-button"
                aria-label="停止生成"
                title="停止生成"
              >
                <Square size={15} fill="currentColor" />
              </ComposerPrimitive.Cancel>
            </AuiIf>
          </div>
        </div>
      </ComposerPrimitive.Root>
      <p className="composer-disclaimer">
        AI 生成内容可能不准确，请核验重要信息。
      </p>
    </div>
  )
}
