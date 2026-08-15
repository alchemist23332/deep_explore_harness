import { AuiIf, useAui } from '@assistant-ui/react'
import { ArrowUpRight, Compass, FileSearch, GitBranch, Sparkles } from 'lucide-react'
import { useWorkspace } from '../../runtime/workspace-context'

const fastPrompts = [
  {
    icon: FileSearch,
    title: '快速总结',
    prompt: '请用三点总结 RAG 的核心原理和适用场景。',
  },
  {
    icon: GitBranch,
    title: '方案比较',
    prompt: '比较 LangChain4j 和 Spring AI 的优缺点。',
  },
  {
    icon: Compass,
    title: '行动建议',
    prompt: '给我一份构建生产级 AI Agent 的最小检查清单。',
  },
]

const deepPrompts = [
  {
    icon: GitBranch,
    title: '架构设计',
    prompt: '为一个支持 RAG、工具调用和多 Agent 的企业应用设计技术架构。',
  },
  {
    icon: FileSearch,
    title: '深入分析',
    prompt: '深入分析本地模型与云端模型混合路由的收益、风险和落地步骤。',
  },
  {
    icon: Compass,
    title: '执行规划',
    prompt: '制定一份从聊天 Demo 演进到生产级 AI Agent 平台的阶段计划。',
  },
]

export function EmptyState() {
  const aui = useAui()
  const { mode } = useWorkspace()
  const prompts = mode === 'FAST' ? fastPrompts : deepPrompts

  return (
    <AuiIf condition={(state) => state.thread.isEmpty}>
      <section className="empty-state">
        <div className="empty-symbol" aria-hidden="true">
          <Sparkles size={22} />
        </div>
        <p className="empty-eyebrow">
          {mode === 'FAST' ? '快速回答' : '深度分析'}
        </p>
        <h2>今天想探索什么？</h2>
        <p className="empty-description">
          提出问题、分析方案，或让 AI 帮你梳理下一步行动。
        </p>

        <div className="prompt-list">
          {prompts.map(({ icon: Icon, title, prompt }) => (
            <button
              type="button"
              className="prompt-button"
              key={title}
              onClick={() => aui.thread.append(prompt)}
            >
              <Icon size={17} />
              <span>
                <strong>{title}</strong>
                <small>{prompt}</small>
              </span>
              <ArrowUpRight size={16} />
            </button>
          ))}
        </div>
      </section>
    </AuiIf>
  )
}
