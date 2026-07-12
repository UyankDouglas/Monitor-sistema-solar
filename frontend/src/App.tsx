import { useMemo, useState, type ReactNode } from 'react'
import { CssBaseline, ThemeProvider, createTheme, CircularProgress, Box } from '@mui/material'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import LoginPage from './pages/LoginPage'
import ChangePasswordPage from './pages/ChangePasswordPage'
import HomePage from './pages/HomePage'

/**
 * Rota protegida: exige sessão; com troca de senha pendente, força a tela
 * de troca antes de qualquer outra.
 */
function Protected({ children }: { children: ReactNode }) {
  const { user, initializing } = useAuth()
  if (initializing) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <CircularProgress />
      </Box>
    )
  }
  if (!user) return <Navigate to="/login" replace />
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />
  return <>{children}</>
}

function AuthenticatedOnly({ children }: { children: ReactNode }) {
  const { user, initializing } = useAuth()
  if (initializing) return null
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function App() {
  const [mode, setMode] = useState<'light' | 'dark'>('light')
  const theme = useMemo(() => createTheme({ palette: { mode } }), [mode])

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/change-password"
              element={(
                <AuthenticatedOnly>
                  <ChangePasswordPage />
                </AuthenticatedOnly>
              )}
            />
            <Route
              path="/"
              element={(
                <Protected>
                  <HomePage mode={mode} onToggleMode={() => setMode(m => (m === 'light' ? 'dark' : 'light'))} />
                </Protected>
              )}
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  )
}
