import { StreamdownTextPrimitive } from '@assistant-ui/react-streamdown'
import { cjk } from '@streamdown/cjk'
import { code } from '@streamdown/code'

export function MarkdownText() {
  return (
    <StreamdownTextPrimitive
      className="markdown-content"
      containerClassName="markdown-container"
      defer
      plugins={{ code, cjk }}
      security={{
        allowedProtocols: ['http', 'https', 'mailto'],
        allowedImagePrefixes: [],
        allowDataImages: false,
      }}
      shikiTheme={['github-light', 'github-dark']}
      translations={{
        copyTable: '复制表格',
        downloadTable: '下载表格',
        viewFullscreen: '查看表格详情',
        exitFullscreen: '关闭表格详情',
      }}
    />
  )
}
