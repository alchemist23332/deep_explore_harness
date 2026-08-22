import {
  FileArchive,
  FilePlus2,
  FileUp,
  FolderMinus,
  FolderPlus,
  RefreshCw,
  Save,
  Trash2,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import {
  importWorkspaceZip,
  readWorkspaceFile,
  uploadWorkspaceFile,
  writeWorkspaceFile,
} from '../../api/workspaces'
import { useSandboxWorkspace } from '../../runtime/sandbox-workspace-context'
import { CodeEditor } from './CodeEditor'
import {
  WorkspaceExplorer,
  type WorkspaceExplorerHandle,
} from './WorkspaceExplorer'

interface EditorBuffer {
  content: string
  savedContent: string
}

export function WorkspaceFilesView({
  workspaceId,
  active,
  onDirtyChange,
}: {
  workspaceId: string
  active: boolean
  onDirtyChange: (dirty: boolean) => void
}) {
  const { selectedFilePath, setSelectedFilePath } = useSandboxWorkspace()
  const [buffers, setBuffers] = useState<Record<string, EditorBuffer>>({})
  const [openPaths, setOpenPaths] = useState<string[]>([])
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const buffersRef = useRef(buffers)
  const explorer = useRef<WorkspaceExplorerHandle>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const zipInput = useRef<HTMLInputElement>(null)
  const activeBuffer = selectedFilePath
    ? buffers[selectedFilePath]
    : undefined
  const dirty = Object.values(buffers).some(
    (buffer) => buffer.content !== buffer.savedContent,
  )

  useEffect(() => {
    buffersRef.current = buffers
  }, [buffers])

  useEffect(() => {
    onDirtyChange(dirty)
  }, [dirty, onDirtyChange])

  useEffect(() => {
    setBuffers({})
    setOpenPaths([])
  }, [workspaceId])

  const openFile = useCallback(async (path: string) => {
    if (buffersRef.current[path]) {
      setSelectedFilePath(path)
      return
    }
    try {
      const file = await readWorkspaceFile(workspaceId, path)
      setBuffers((current) => ({
        ...current,
        [file.path]: {
          content: file.content,
          savedContent: file.content,
        },
      }))
      setOpenPaths((current) =>
        current.includes(file.path) ? current : [...current, file.path],
      )
      setSelectedFilePath(file.path)
    } catch (error) {
      toast.error((error as Error).message)
    }
  }, [setSelectedFilePath, workspaceId])

  useEffect(() => {
    if (selectedFilePath && !buffersRef.current[selectedFilePath]) {
      void openFile(selectedFilePath)
    }
  }, [openFile, selectedFilePath])

  const updateContent = (content: string) => {
    if (!selectedFilePath) return
    setBuffers((current) => ({
      ...current,
      [selectedFilePath]: {
        ...current[selectedFilePath],
        content,
      },
    }))
  }

  const save = async () => {
    if (!selectedFilePath || !activeBuffer) return
    setSaving(true)
    try {
      const file = await writeWorkspaceFile(
        workspaceId,
        selectedFilePath,
        activeBuffer.content,
      )
      setBuffers((current) => ({
        ...current,
        [file.path]: {
          content: file.content,
          savedContent: file.content,
        },
      }))
      setRefreshKey((value) => value + 1)
      toast.success('文件已保存')
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setSaving(false)
    }
  }

  useEffect(() => {
    if (!active) return
    const handleKeyDown = (event: KeyboardEvent) => {
      if (
        (event.metaKey || event.ctrlKey) &&
        event.key.toLowerCase() === 's'
      ) {
        event.preventDefault()
        if (
          activeBuffer &&
          activeBuffer.content !== activeBuffer.savedContent &&
          !saving
        ) {
          void save()
        }
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  })

  const closeTab = (path: string) => {
    const buffer = buffers[path]
    if (
      buffer &&
      buffer.content !== buffer.savedContent &&
      !window.confirm(`“${fileName(path)}”尚未保存，仍要关闭？`)
    ) {
      return
    }
    const index = openPaths.indexOf(path)
    const remaining = openPaths.filter((candidate) => candidate !== path)
    setOpenPaths(remaining)
    setBuffers((current) => {
      const next = { ...current }
      delete next[path]
      return next
    })
    if (selectedFilePath === path) {
      setSelectedFilePath(
        remaining[Math.min(index, remaining.length - 1)] ?? null,
      )
    }
  }

  const upload = async (file: File, zip: boolean) => {
    setUploading(true)
    try {
      if (zip) {
        const result = await importWorkspaceZip(workspaceId, file)
        await explorer.current?.reload()
        toast.success(`已导入 ${result.files} 个文件`)
      } else {
        const parent = explorer.current?.selectedDirectory() ?? ''
        const path = parent ? `${parent}/${file.name}` : file.name
        const result = await uploadWorkspaceFile(workspaceId, file, path)
        await explorer.current?.reload()
        await openFile(result.path)
        toast.success(`已上传到 /workspace/${result.path}`)
      }
    } catch (error) {
      toast.error((error as Error).message)
    } finally {
      setUploading(false)
    }
  }

  const handlePathMoved = (sourcePath: string, targetPath: string) => {
    setOpenPaths((current) =>
      current.map((path) => remapPath(path, sourcePath, targetPath)),
    )
    setBuffers((current) =>
      remapBuffers(current, sourcePath, targetPath),
    )
    if (selectedFilePath) {
      setSelectedFilePath(
        remapPath(selectedFilePath, sourcePath, targetPath),
      )
    }
  }

  const handlePathsDeleted = (paths: string[]) => {
    const deleted = (path: string) =>
      paths.some(
        (deletedPath) =>
          path === deletedPath || path.startsWith(`${deletedPath}/`),
      )
    setOpenPaths((current) => current.filter((path) => !deleted(path)))
    setBuffers((current) =>
      Object.fromEntries(
        Object.entries(current).filter(([path]) => !deleted(path)),
      ),
    )
    if (selectedFilePath && deleted(selectedFilePath)) {
      setSelectedFilePath(null)
    }
  }

  return (
    <div
      className="workspace-files-view"
      data-active={active}
      aria-hidden={!active}
    >
      <div className="workspace-files-toolbar">
        <button
          type="button"
          className="icon-button compact"
          onClick={() => explorer.current?.beginCreate('FILE')}
          title="新建文件"
          aria-label="新建文件"
        >
          <FilePlus2 size={15} />
        </button>
        <button
          type="button"
          className="icon-button compact"
          onClick={() => explorer.current?.beginCreate('DIRECTORY')}
          title="新建文件夹"
          aria-label="新建文件夹"
        >
          <FolderPlus size={15} />
        </button>
        <button
          type="button"
          className="icon-button compact"
          onClick={() => explorer.current?.collapseAll()}
          title="折叠全部文件夹"
          aria-label="折叠全部文件夹"
        >
          <FolderMinus size={15} />
        </button>
        <span className="workspace-file-path">
          {selectedFilePath ?? '工作区'}
        </span>
        <button
          type="button"
          className="icon-button compact"
          disabled={uploading}
          onClick={() => fileInput.current?.click()}
          title={uploading ? '正在上传' : '上传文件'}
          aria-label="上传文件"
        >
          {uploading ? (
            <RefreshCw className="upload-spinner" size={15} />
          ) : (
            <FileUp size={15} />
          )}
        </button>
        <button
          type="button"
          className="icon-button compact"
          disabled={uploading}
          onClick={() => zipInput.current?.click()}
          title="导入 ZIP"
          aria-label="导入 ZIP"
        >
          <FileArchive size={15} />
        </button>
        <button
          type="button"
          className="icon-button compact"
          disabled={
            !activeBuffer ||
            activeBuffer.content === activeBuffer.savedContent ||
            saving
          }
          onClick={() => void save()}
          title="保存文件（Cmd/Ctrl + S）"
          aria-label="保存文件"
        >
          <Save size={15} />
        </button>
        <button
          type="button"
          className="icon-button compact"
          onClick={() => explorer.current?.deleteSelected()}
          title="删除所选项"
          aria-label="删除所选项"
        >
          <Trash2 size={15} />
        </button>
      </div>

      <div className="workspace-files-body">
        <section className="embedded-file-tree">
          <WorkspaceExplorer
            ref={explorer}
            workspaceId={workspaceId}
            refreshKey={refreshKey}
            selectedPath={selectedFilePath}
            onSelectFile={openFile}
            onPathMoved={handlePathMoved}
            onPathsDeleted={handlePathsDeleted}
            onEntryCreated={(entry) => {
              if (entry.type === 'FILE') void openFile(entry.path)
            }}
          />
        </section>
        <section className="workspace-editor-pane">
          {openPaths.length > 0 && (
            <div className="workspace-editor-tabs" role="tablist">
              {openPaths.map((path) => {
                const buffer = buffers[path]
                const tabDirty =
                  buffer && buffer.content !== buffer.savedContent
                return (
                  <div
                    key={path}
                    className={`workspace-editor-tab ${
                      path === selectedFilePath ? 'active' : ''
                    }`}
                  >
                    <button
                      type="button"
                      role="tab"
                      aria-selected={path === selectedFilePath}
                      title={path}
                      onClick={() => setSelectedFilePath(path)}
                    >
                      <span>{fileName(path)}</span>
                      {tabDirty && <i />}
                    </button>
                    <button
                      type="button"
                      className="workspace-tab-close"
                      onClick={() => closeTab(path)}
                      title="关闭文件"
                      aria-label={`关闭 ${fileName(path)}`}
                    >
                      <X size={12} />
                    </button>
                  </div>
                )
              })}
            </div>
          )}
          <div className="workspace-editor-content">
            <CodeEditor
              path={selectedFilePath}
              value={activeBuffer?.content ?? ''}
              onChange={updateContent}
            />
          </div>
        </section>
      </div>

      <input
        ref={fileInput}
        type="file"
        className="sr-only"
        aria-label="选择要上传的文件"
        onChange={(event) => {
          const file = event.target.files?.[0]
          if (file) void upload(file, false)
          event.target.value = ''
        }}
      />
      <input
        ref={zipInput}
        type="file"
        accept=".zip,application/zip"
        className="sr-only"
        aria-label="选择要导入的 ZIP"
        onChange={(event) => {
          const file = event.target.files?.[0]
          if (file) void upload(file, true)
          event.target.value = ''
        }}
      />
    </div>
  )
}

function fileName(path: string) {
  return path.split('/').at(-1) ?? path
}

function remapPath(path: string, source: string, target: string) {
  if (path === source) return target
  return path.startsWith(`${source}/`)
    ? `${target}${path.slice(source.length)}`
    : path
}

function remapBuffers(
  buffers: Record<string, EditorBuffer>,
  source: string,
  target: string,
) {
  return Object.fromEntries(
    Object.entries(buffers).map(([path, buffer]) => [
      remapPath(path, source, target),
      buffer,
    ]),
  )
}
