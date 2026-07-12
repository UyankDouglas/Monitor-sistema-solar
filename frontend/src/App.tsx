import { useMemo, useState, type ReactNode } from 'react'
import { Box, CircularProgress, CssBaseline, ThemeProvider, createTheme } from '@mui/material'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { RealtimeProvider } from './realtime/RealtimeContext'
import AppLayout from './layout/AppLayout'
import LoginPage from './pages/LoginPage'
import ChangePasswordPage from './pages/ChangePasswordPage'
import DashboardPage from './pages/DashboardPage'
import HistoryPage from './pages/HistoryPage'
import WeatherPage from './pages/WeatherPage'
import AlertsPage from './pages/AlertsPage'
import SettingsPage from './pages/SettingsPage'
import UsersPage from './pages/UsersPage'

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
  if (user.mustChangePassword) return <Navigate to="/trocar-senha" replace />
  return <>{children}</>
}

function AuthenticatedOnly({ children }: { children: ReactNode }) {
  const { user, initializing } = useAuth()
  if (initializing) return null
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}

/** Páginas exclusivas de ADMIN — USER comum volta ao dashboard. */
function AdminOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (!user?.roles.includes('ADMIN')) return <Navigate to="/" replace />
  return <>{children}</>
}

export default function App() {
  const [mode, setMode] = useState<'light' | 'dark'>(() =>
    (localStorage.getItem('solar.theme') as 'light' | 'dark' | null) ?? 'light')
  const theme = useMemo(() => createTheme({ palette: { mode } }), [mode])

  function toggleMode() {
    setMode(m => {
      const next = m === 'light' ? 'dark' : 'light'
      localStorage.setItem('solar.theme', next)
      return next
    })
  }

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/trocar-senha"
              element={(
                <AuthenticatedOnly>
                  <ChangePasswordPage />
                </AuthenticatedOnly>
              )}
            />
            {/* Compatibilidade com o caminho antigo */}
            <Route path="/change-password" element={<Navigate to="/trocar-senha" replace />} />

            <Route
              element={(
                <Protected>
                  <RealtimeProvider>
                    <AppLayout mode={mode} onToggleMode={toggleMode} />
                  </RealtimeProvider>
                </Protected>
              )}
            >
              <Route path="/" element={<DashboardPage mode={mode} />} />
              <Route path="/historico" element={<HistoryPage mode={mode} />} />
              <Route path="/clima" element={<WeatherPage mode={mode} />} />
              <Route path="/alertas" element={<AlertsPage />} />
              <Route path="/configuracoes" element={<AdminOnly><SettingsPage /></AdminOnly>} />
              <Route path="/usuarios" element={<AdminOnly><UsersPage /></AdminOnly>} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  )
}
