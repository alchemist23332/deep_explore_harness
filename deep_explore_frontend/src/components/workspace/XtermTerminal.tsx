import { FitAddon } from '@xterm/addon-fit'
import { WebLinksAddon } from '@xterm/addon-web-links'
import { Terminal } from '@xterm/xterm'
import '@xterm/xterm/css/xterm.css'
import { Eraser, PlugZap, RotateCw } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { workspaceTerminalUrl } from '../../api/workspaces'
import { useWorkspace } from '../../runtime/workspace-context'

type ConnectionStatus =
  | 'idle'
  | 'starting'
  | 'connecting'
  | 'connected'
  | 'disconnected'
  | 'error'

export function XtermTerminal({
  workspaceId,
  active,
  lifecycleBusy,
  onEnsureRunning,
  onConnectionBusyChange,
}: {
  workspaceId: string
  active: boolean
  lifecycleBusy: boolean
  onEnsureRunning: () => Promise<void>
  onConnectionBusyChange: (busy: boolean) => void
}) {
  const { theme } = useWorkspace()
  const container = useRef<HTMLDivElement>(null)
  const terminal = useRef<Terminal | null>(null)
  const fitAddon = useRef<FitAddon | null>(null)
  const socket = useRef<WebSocket | null>(null)
  const disposed = useRef(false)
  const connecting = useRef(false)
  const activeRef = useRef(active)
  const [status, setStatus] = useState<ConnectionStatus>('idle')

  useEffect(() => {
    activeRef.current = active
  }, [active])

  useEffect(() => {
    const host = container.current
    if (!host) return
    disposed.current = false
    const nextTerminal = new Terminal({
      cursorBlink: true,
      cursorStyle: 'bar',
      fontFamily:
        'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
      fontSize: 13,
      fontWeight: '500',
      lineHeight: 1.3,
      scrollback: 8_000,
      screenReaderMode: true,
      allowProposedApi: false,
      theme: terminalTheme(
        document.documentElement.dataset.theme === 'dark'
          ? 'dark'
          : 'light',
      ),
    })
    const nextFitAddon = new FitAddon()
    nextTerminal.loadAddon(nextFitAddon)
    nextTerminal.loadAddon(new WebLinksAddon())
    nextTerminal.open(host)
    terminal.current = nextTerminal
    fitAddon.current = nextFitAddon

    const input = nextTerminal.onData((data) => {
      if (socket.current?.readyState === WebSocket.OPEN) {
        socket.current.send(JSON.stringify({ type: 'input', data }))
      }
    })
    const resize = nextTerminal.onResize(({ cols, rows }) => {
      if (socket.current?.readyState === WebSocket.OPEN) {
        socket.current.send(
          JSON.stringify({ type: 'resize', columns: cols, rows }),
        )
      }
    })
    const observer = new ResizeObserver(() => {
      if (activeRef.current) {
        requestAnimationFrame(() => nextFitAddon.fit())
      }
    })
    observer.observe(host)

    return () => {
      disposed.current = true
      observer.disconnect()
      input.dispose()
      resize.dispose()
      socket.current?.close()
      socket.current = null
      nextTerminal.dispose()
      terminal.current = null
      fitAddon.current = null
    }
  }, [workspaceId])

  useEffect(() => {
    if (terminal.current) {
      terminal.current.options.theme = terminalTheme(theme)
    }
  }, [theme])

  const connect = useCallback(async () => {
    if (
      connecting.current ||
      socket.current?.readyState === WebSocket.OPEN
    ) {
      return
    }
    const term = terminal.current
    if (!term) return
    connecting.current = true
    onConnectionBusyChange(true)
    setStatus('starting')
    term.writeln('\x1b[38;5;250mStarting sandbox terminal...\x1b[0m')
    try {
      await onEnsureRunning()
      if (disposed.current) return
      setStatus('connecting')
      const nextSocket = new WebSocket(workspaceTerminalUrl(workspaceId))
      nextSocket.binaryType = 'arraybuffer'
      socket.current = nextSocket

      nextSocket.onopen = () => {
        if (disposed.current) {
          nextSocket.close()
          return
        }
        setStatus('connected')
        connecting.current = false
        onConnectionBusyChange(false)
        requestAnimationFrame(() => {
          fitAddon.current?.fit()
          nextSocket.send(
            JSON.stringify({
              type: 'resize',
              columns: terminal.current?.cols ?? 100,
              rows: terminal.current?.rows ?? 30,
            }),
          )
          terminal.current?.focus()
        })
      }
      nextSocket.onmessage = (event) => {
        if (typeof event.data === 'string') {
          handleControlMessage(term, event.data, setStatus)
          return
        }
        if (event.data instanceof ArrayBuffer) {
          term.write(new Uint8Array(event.data))
          return
        }
        if (event.data instanceof Blob) {
          void event.data.arrayBuffer().then((buffer) => {
            if (!disposed.current) {
              term.write(new Uint8Array(buffer))
            }
          })
        }
      }
      nextSocket.onerror = () => {
        setStatus('error')
        term.writeln(
          '\r\n\x1b[1;31mTerminal connection failed.\x1b[0m',
        )
      }
      nextSocket.onclose = () => {
        socket.current = null
        connecting.current = false
        onConnectionBusyChange(false)
        if (!disposed.current) {
          setStatus('disconnected')
          term.writeln(
            '\r\n\x1b[38;5;244mTerminal session closed.\x1b[0m',
          )
        }
      }
    } catch (error) {
      connecting.current = false
      onConnectionBusyChange(false)
      setStatus('error')
      term.writeln(
        `\r\n\x1b[1;31m${(error as Error).message}\x1b[0m`,
      )
    }
  }, [onConnectionBusyChange, onEnsureRunning, workspaceId])

  useEffect(() => {
    if (!active) return
    requestAnimationFrame(() => {
      fitAddon.current?.fit()
      terminal.current?.focus()
    })
    if (status === 'idle') void connect()
  }, [active, connect, status])

  const reconnect = () => {
    socket.current?.close()
    socket.current = null
    connecting.current = false
    terminal.current?.clear()
    setStatus('idle')
    void connect()
  }

  return (
    <section className="xterm-terminal-view">
      <header className="xterm-terminal-toolbar">
        <span className={`terminal-connection-state ${status}`}>
          <PlugZap size={13} />
          {statusLabel(status)}
        </span>
        <div>
          <button
            type="button"
            className="icon-button compact"
            disabled={lifecycleBusy || status === 'starting'}
            onClick={reconnect}
            title="重新连接终端"
            aria-label="重新连接终端"
          >
            <RotateCw size={14} />
          </button>
          <button
            type="button"
            className="icon-button compact"
            onClick={() => terminal.current?.clear()}
            title="清空终端"
            aria-label="清空终端"
          >
            <Eraser size={14} />
          </button>
        </div>
      </header>
      <div
        ref={container}
        className="xterm-terminal-host"
        role="application"
        tabIndex={0}
        aria-label="交互式终端"
        onClick={() => terminal.current?.focus()}
        onFocus={() => terminal.current?.focus()}
      />
    </section>
  )
}

function handleControlMessage(
  terminal: Terminal,
  payload: string,
  setStatus: (status: ConnectionStatus) => void,
) {
  try {
    const event = JSON.parse(payload) as {
      type?: string
      message?: string
    }
    if (event.type === 'error') {
      setStatus('error')
      terminal.writeln(
        `\r\n\x1b[1;31m${event.message ?? 'Terminal error'}\x1b[0m`,
      )
    }
    if (event.type === 'exit') {
      setStatus('disconnected')
    }
  } catch {
    terminal.write(payload)
  }
}

function terminalTheme(theme: 'light' | 'dark') {
  return theme === 'light'
    ? {
        background: '#ffffff',
        foreground: '#17212b',
        cursor: '#0969da',
        cursorAccent: '#ffffff',
        selectionBackground: '#b6d7ff',
        black: '#24292f',
        red: '#cf222e',
        green: '#116329',
        yellow: '#7d4e00',
        blue: '#0550ae',
        magenta: '#8250df',
        cyan: '#1b7c83',
        white: '#6e7781',
        brightBlack: '#57606a',
        brightRed: '#a40e26',
        brightGreen: '#1a7f37',
        brightYellow: '#9a6700',
        brightBlue: '#0969da',
        brightMagenta: '#8250df',
        brightCyan: '#3192aa',
        brightWhite: '#24292f',
      }
    : {
        background: '#0d1117',
        foreground: '#f0f6fc',
        cursor: '#58a6ff',
        cursorAccent: '#0d1117',
        selectionBackground: '#264f78',
        black: '#484f58',
        red: '#ff7b72',
        green: '#7ee787',
        yellow: '#e3b341',
        blue: '#79c0ff',
        magenta: '#d2a8ff',
        cyan: '#76e3ea',
        white: '#b1bac4',
        brightBlack: '#6e7681',
        brightRed: '#ffa198',
        brightGreen: '#56d364',
        brightYellow: '#f2cc60',
        brightBlue: '#a5d6ff',
        brightMagenta: '#d2a8ff',
        brightCyan: '#b3f0ff',
        brightWhite: '#ffffff',
      }
}

function statusLabel(status: ConnectionStatus) {
  switch (status) {
    case 'starting':
      return '正在启动'
    case 'connecting':
      return '正在连接'
    case 'connected':
      return '已连接'
    case 'error':
      return '连接错误'
    case 'disconnected':
      return '已断开'
    default:
      return '未连接'
  }
}
