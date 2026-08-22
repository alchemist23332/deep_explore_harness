import {
  ChevronDown,
  ChevronRight,
  File,
  FileCode2,
  Folder,
  FolderOpen,
} from 'lucide-react'
import {
  createContext,
  forwardRef,
  useCallback,
  useContext,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type MouseEvent,
  type SyntheticEvent,
} from 'react'
import {
  Tree,
  type NodeApi,
  type NodeRendererProps,
  type TreeApi,
} from 'react-arborist'
import { toast } from 'sonner'
import {
  createWorkspaceEntry,
  deleteWorkspaceEntry,
  getWorkspaceTree,
  moveWorkspaceEntry,
  workspaceEventsUrl,
  type WorkspaceEntry,
  type WorkspaceTreeNode,
} from '../../api/workspaces'

interface ExplorerNodeData {
  id: string
  name: string
  type: WorkspaceEntry['type']
  children: ExplorerNodeData[] | null
}

export interface WorkspaceExplorerHandle {
  reload: () => Promise<boolean>
  beginCreate: (type: WorkspaceEntry['type']) => void
  renameSelected: () => void
  deleteSelected: () => void
  collapseAll: () => void
  selectedDirectory: () => string
}

interface ExplorerActions {
  openFile: (path: string) => void
  openContextMenu: (
    node: NodeApi<ExplorerNodeData>,
    event: MouseEvent,
  ) => void
}

const ExplorerActionsContext = createContext<ExplorerActions | null>(null)

export const WorkspaceExplorer = forwardRef<
  WorkspaceExplorerHandle,
  {
    workspaceId: string
    refreshKey: number
    selectedPath: string | null
    onSelectFile: (path: string) => void
    onPathMoved: (sourcePath: string, targetPath: string) => void
    onPathsDeleted: (paths: string[]) => void
    onEntryCreated: (entry: WorkspaceEntry) => void
  }
>(function WorkspaceExplorer(
  {
    workspaceId,
    refreshKey,
    selectedPath,
    onSelectFile,
    onPathMoved,
    onPathsDeleted,
    onEntryCreated,
  },
  ref,
) {
  const [data, setData] = useState<ExplorerNodeData[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedNode, setSelectedNode] =
    useState<NodeApi<ExplorerNodeData> | null>(null)
  const [creating, setCreating] = useState<{
    type: WorkspaceEntry['type']
    parentPath: string
  } | null>(null)
  const [contextMenu, setContextMenu] = useState<{
    x: number
    y: number
    node: NodeApi<ExplorerNodeData>
  } | null>(null)
  const requestSequence = useRef(0)
  const tree = useRef<TreeApi<ExplorerNodeData> | null>(null)
  const container = useRef<HTMLDivElement>(null)
  const [size, setSize] = useState({ width: 220, height: 320 })

  const reload = useCallback(async () => {
    const request = ++requestSequence.current
    setLoading(true)
    setError(null)
    try {
      const next = await getWorkspaceTree(workspaceId)
      if (request !== requestSequence.current) return false
      setData(next.map(toExplorerNode))
      return true
    } catch (loadError) {
      if (request !== requestSequence.current) return false
      setError((loadError as Error).message)
      return false
    } finally {
      if (request === requestSequence.current) setLoading(false)
    }
  }, [workspaceId])

  const beginCreate = useCallback(
    (type: WorkspaceEntry['type']) => {
      const current = selectedNode?.data
      const parentPath =
        current?.type === 'DIRECTORY'
          ? current.id
          : parentOf(current?.id ?? selectedPath ?? '')
      setCreating({ type, parentPath })
      setContextMenu(null)
    },
    [selectedNode, selectedPath],
  )

  const deleteNodes = useCallback(
    async (nodes: NodeApi<ExplorerNodeData>[]) => {
      if (nodes.length === 0) return
      const paths = nodes.map((node) => node.data.id)
      if (
        !window.confirm(
          paths.length === 1
            ? `删除“${nodes[0].data.name}”？`
            : `删除选中的 ${paths.length} 项？`,
        )
      ) {
        return
      }
      try {
        await Promise.all(
          paths.map((path) =>
            deleteWorkspaceEntry(workspaceId, path, true),
          ),
        )
        onPathsDeleted(paths)
        await reload()
      } catch (deleteError) {
        toast.error((deleteError as Error).message)
      }
    },
    [onPathsDeleted, reload, workspaceId],
  )

  useImperativeHandle(
    ref,
    () => ({
      reload,
      beginCreate,
      renameSelected: () => {
        if (selectedNode) void selectedNode.edit()
      },
      deleteSelected: () => {
        const selected = tree.current?.selectedNodes ?? []
        void deleteNodes(selected)
      },
      collapseAll: () => tree.current?.closeAll(),
      selectedDirectory: () => {
        const selected = selectedNode?.data
        return selected?.type === 'DIRECTORY'
          ? selected.id
          : parentOf(selected?.id ?? selectedPath ?? '')
      },
    }),
    [beginCreate, deleteNodes, reload, selectedNode, selectedPath],
  )

  useEffect(() => {
    void reload()
    return () => {
      requestSequence.current += 1
    }
  }, [refreshKey, reload])

  useEffect(() => {
    const element = container.current
    if (!element) return
    const update = () =>
      setSize({
        width: Math.max(160, element.clientWidth),
        height: Math.max(120, element.clientHeight),
      })
    update()
    const observer = new ResizeObserver(update)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    let timeout: number | undefined
    const events = new EventSource(workspaceEventsUrl(workspaceId))
    events.addEventListener('workspace_change', () => {
      window.clearTimeout(timeout)
      timeout = window.setTimeout(() => void reload(), 180)
    })
    return () => {
      window.clearTimeout(timeout)
      events.close()
    }
  }, [reload, workspaceId])

  useEffect(() => {
    const closeMenu = () => setContextMenu(null)
    window.addEventListener('click', closeMenu)
    window.addEventListener('blur', closeMenu)
    return () => {
      window.removeEventListener('click', closeMenu)
      window.removeEventListener('blur', closeMenu)
    }
  }, [])

  const actions = useMemo<ExplorerActions>(
    () => ({
      openFile: onSelectFile,
      openContextMenu: (node, event) => {
        event.preventDefault()
        event.stopPropagation()
        node.select()
        setSelectedNode(node)
        setContextMenu({
          x: event.clientX,
          y: event.clientY,
          node,
        })
      },
    }),
    [onSelectFile],
  )

  const createEntry = async (
    event: SyntheticEvent<HTMLFormElement, SubmitEvent>,
  ) => {
    event.preventDefault()
    if (!creating) return
    const form = new FormData(event.currentTarget)
    const name = String(form.get('name') ?? '').trim()
    if (!name) return
    try {
      const entry = await createWorkspaceEntry(
        workspaceId,
        creating.parentPath,
        name,
        creating.type,
      )
      setCreating(null)
      await reload()
      tree.current?.open(creating.parentPath)
      onEntryCreated(entry)
    } catch (createError) {
      toast.error((createError as Error).message)
    }
  }

  if (loading && data.length === 0) {
    return <div className="file-tree-status">读取工作区</div>
  }
  if (error && data.length === 0) {
    return (
      <div className="file-tree-status error">
        <span>{error}</span>
        <button type="button" onClick={() => void reload()}>
          重试
        </button>
      </div>
    )
  }

  return (
    <ExplorerActionsContext.Provider value={actions}>
      <div
        ref={container}
        className="workspace-explorer"
        onKeyDown={(event) => {
          if (event.key === 'F2' && selectedNode) {
            event.preventDefault()
            void selectedNode.edit()
          }
          if (event.key === 'Delete') {
            event.preventDefault()
            void deleteNodes(tree.current?.selectedNodes ?? [])
          }
        }}
      >
        {creating && (
          <form className="explorer-create-row" onSubmit={createEntry}>
            {creating.type === 'DIRECTORY' ? (
              <Folder size={14} />
            ) : (
              <File size={14} />
            )}
            <input
              name="name"
              aria-label={
                creating.type === 'DIRECTORY'
                  ? '新建文件夹名称'
                  : '新建文件名称'
              }
              autoFocus
              onKeyDown={(event) => {
                if (event.key === 'Escape') setCreating(null)
              }}
              onBlur={(event) => {
                if (!event.currentTarget.value.trim()) setCreating(null)
              }}
            />
          </form>
        )}
        {data.length === 0 ? (
          <div className="file-tree-status">工作区为空</div>
        ) : (
          <Tree
            ref={tree}
            data={data}
            width={size.width}
            height={size.height}
            rowHeight={28}
            indent={16}
            overscanCount={8}
            openByDefault={false}
            selection={selectedPath ?? undefined}
            initialOpenState={readOpenState(workspaceId)}
            onToggle={() =>
              window.localStorage.setItem(
                openStateKey(workspaceId),
                JSON.stringify(tree.current?.openState ?? {}),
              )
            }
            onActivate={(node) => {
              if (node.data.type === 'FILE') onSelectFile(node.data.id)
              else node.toggle()
            }}
            onSelect={(nodes) => setSelectedNode(nodes.at(-1) ?? null)}
            onRename={async ({ id, name }) => {
              const targetPath = joinPath(parentOf(id), name)
              if (targetPath === id) return
              await moveWorkspaceEntry(workspaceId, id, targetPath)
              onPathMoved(id, targetPath)
              await reload()
            }}
            onMove={async ({ dragNodes, parentId }) => {
              for (const node of dragNodes) {
                const targetPath = joinPath(
                  parentId ?? '',
                  node.data.name,
                )
                if (targetPath === node.data.id) continue
                await moveWorkspaceEntry(
                  workspaceId,
                  node.data.id,
                  targetPath,
                )
                onPathMoved(node.data.id, targetPath)
              }
              await reload()
            }}
            onDelete={({ nodes }) => deleteNodes(nodes)}
            aria-label="工作区文件"
          >
            {ExplorerNode}
          </Tree>
        )}
        {contextMenu && (
          <div
            className="explorer-context-menu"
            style={{ left: contextMenu.x, top: contextMenu.y }}
            onClick={(event) => event.stopPropagation()}
          >
            <button type="button" onClick={() => beginCreate('FILE')}>
              新建文件
            </button>
            <button type="button" onClick={() => beginCreate('DIRECTORY')}>
              新建文件夹
            </button>
            <button
              type="button"
              onClick={() => {
                setContextMenu(null)
                void contextMenu.node.edit()
              }}
            >
              重命名
            </button>
            <button
              type="button"
              className="danger"
              onClick={() => {
                setContextMenu(null)
                void deleteNodes([contextMenu.node])
              }}
            >
              删除
            </button>
          </div>
        )}
      </div>
    </ExplorerActionsContext.Provider>
  )
})

function ExplorerNode({
  node,
  style,
  dragHandle,
}: NodeRendererProps<ExplorerNodeData>) {
  const actions = useContext(ExplorerActionsContext)
  const input = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (node.isEditing) {
      input.current?.focus()
      input.current?.select()
    }
  }, [node.isEditing])

  const rowStyle: CSSProperties = {
    ...style,
    paddingLeft: `${node.level * 16 + 6}px`,
  }
  return (
    <div
      ref={dragHandle}
      style={rowStyle}
      className={`explorer-node ${
        node.isSelected ? 'selected' : ''
      }`}
      onContextMenu={(event) =>
        actions?.openContextMenu(node, event)
      }
    >
      <button
        type="button"
        className="explorer-node-toggle"
        tabIndex={-1}
        aria-label={node.isOpen ? '折叠文件夹' : '展开文件夹'}
        disabled={node.isLeaf}
        onClick={(event) => {
          event.stopPropagation()
          node.toggle()
        }}
      >
        {node.isLeaf ? null : node.isOpen ? (
          <ChevronDown size={13} />
        ) : (
          <ChevronRight size={13} />
        )}
      </button>
      {node.data.type === 'DIRECTORY' ? (
        node.isOpen ? (
          <FolderOpen className="folder" size={15} />
        ) : (
          <Folder className="folder" size={15} />
        )
      ) : isCodeFile(node.data.name) ? (
        <FileCode2 className="file" size={15} />
      ) : (
        <File className="file" size={15} />
      )}
      {node.isEditing ? (
        <input
          ref={input}
          className="explorer-rename-input"
          defaultValue={node.data.name}
          onBlur={() => node.reset()}
          onKeyDown={(event) => {
            if (event.key === 'Escape') node.reset()
            if (event.key === 'Enter') {
              void node.submit(event.currentTarget.value.trim())
            }
          }}
        />
      ) : (
        <button
          type="button"
          className="explorer-node-label"
          title={node.data.id}
          onClick={(event) => {
            event.stopPropagation()
            node.select()
            if (node.data.type === 'DIRECTORY') node.toggle()
            else actions?.openFile(node.data.id)
          }}
        >
          {node.data.name}
        </button>
      )}
    </div>
  )
}

function toExplorerNode(node: WorkspaceTreeNode): ExplorerNodeData {
  return {
    id: node.entry.path,
    name: node.entry.name,
    type: node.entry.type,
    children:
      node.entry.type === 'DIRECTORY'
        ? (node.children ?? []).map(toExplorerNode)
        : null,
  }
}

function parentOf(path: string) {
  const separator = path.lastIndexOf('/')
  return separator < 0 ? '' : path.slice(0, separator)
}

function joinPath(parent: string, name: string) {
  return parent ? `${parent}/${name}` : name
}

function isCodeFile(name: string) {
  return /\.(java|kt|kts|js|jsx|ts|tsx|json|xml|ya?ml|md|css|html)$/i.test(
    name,
  )
}

function openStateKey(workspaceId: string) {
  return `deep-explore:sandbox:explorer-open:${workspaceId}`
}

function readOpenState(workspaceId: string) {
  try {
    const stored = window.localStorage.getItem(openStateKey(workspaceId))
    return stored ? JSON.parse(stored) : {}
  } catch {
    return {}
  }
}
