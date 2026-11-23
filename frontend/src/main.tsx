import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material'
import App from './App.tsx'
import { ApiProvider } from './api/ApiProvider.tsx'
import { AuthProvider } from './auth/AuthProvider.tsx'

const theme = createTheme({
  colorSchemes: { light: true, dark: true },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <ApiProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ApiProvider>
    </ThemeProvider>
  </StrictMode>,
)
