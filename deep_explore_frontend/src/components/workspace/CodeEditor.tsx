import CodeMirror from '@uiw/react-codemirror'
import { java } from '@codemirror/lang-java'
import { json } from '@codemirror/lang-json'
import { markdown } from '@codemirror/lang-markdown'
import { useWorkspace } from '../../runtime/workspace-context'

export function CodeEditor({
  path,
  value,
  onChange,
}: {
  path: string | null
  value: string
  onChange: (value: string) => void
}) {
  const { theme } = useWorkspace()

  if (!path) {
    return (
      <div className="editor-empty">
        <strong>选择一个文件</strong>
        <span>文件内容会显示在这里</span>
      </div>
    )
  }

  return (
    <section className="sandbox-editor">
      <CodeMirror
        value={value}
        height="100%"
        theme={theme}
        extensions={languageExtensions(path)}
        onChange={onChange}
        basicSetup={{
          foldGutter: true,
          lineNumbers: true,
          highlightActiveLine: true,
        }}
      />
    </section>
  )
}

function languageExtensions(path: string) {
  const extension = path.split('.').pop()?.toLowerCase()
  if (extension === 'java') return [java()]
  if (extension === 'json') return [json()]
  if (extension === 'md' || extension === 'markdown') return [markdown()]
  return []
}
