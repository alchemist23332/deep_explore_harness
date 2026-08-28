import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { DeepExploreRuntimeProvider } from './runtime/DeepExploreRuntimeProvider'
import { WorkbenchSandboxProvider } from './runtime/WorkbenchSandboxProvider'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <WorkbenchSandboxProvider>
        <DeepExploreRuntimeProvider>
          <App />
        </DeepExploreRuntimeProvider>
      </WorkbenchSandboxProvider>
    </BrowserRouter>
  </StrictMode>,
)
