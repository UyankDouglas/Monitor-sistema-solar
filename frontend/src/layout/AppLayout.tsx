import { useEffect, useState } from 'react'
import { Outlet, NavLink, useLocation } from 'react-router-dom'
import {
  Alert as MuiAlert, AppBar, Avatar, Badge, Box, Divider, Drawer, IconButton,
  List, ListItemButton, ListItemIcon, ListItemText, Menu, MenuItem, Snackbar,
  Toolbar, Tooltip, Typography, useMediaQuery, useTheme,
} from '@mui/material'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import MenuIcon from '@mui/icons-material/Menu'
import BoltIcon from '@mui/icons-material/Bolt'
import DashboardIcon from '@mui/icons-material/Dashboard'
import HistoryIcon from '@mui/icons-material/History'
import CloudIcon from '@mui/icons-material/Cloud'
import NotificationsIcon from '@mui/icons-material/Notifications'
import SettingsIcon from '@mui/icons-material/Settings'
import GroupIcon from '@mui/icons-material/Group'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import LockResetIcon from '@mui/icons-material/LockReset'
import LogoutIcon from '@mui/icons-material/Logout'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useRealtime, type AlertEvent } from '../realtime/RealtimeContext'
import { api } from '../api/client'
import type { Dashboard } from '../api/types'

const DRAWER_WIDTH = 232

/**
 * Notificação nativa em melhor esforço. No Chrome Android o construtor
 * `new Notification()` lança "Illegal constructor" — lá o caminho é via
 * service worker (que o PWA registra); em desktop o construtor funciona.
 */
function showNativeNotification(message: string) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return
  try {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.getRegistration()
        .then(registration => {
          if (registration) {
            void registration.showNotification('Monitor Solar — alerta', { body: message })
          } else {
            new Notification('Monitor Solar — alerta', { body: message })
          }
        })
        .catch(() => undefined)
    } else {
      new Notification('Monitor Solar — alerta', { body: message })
    }
  } catch {
    // melhor esforço: sem notificação nativa, o toast in-app já cobre
  }
}

interface Props {
  mode: 'light' | 'dark'
  onToggleMode: () => void
}

/** Casca da aplicação: sidebar de navegação + navbar + conteúdo roteado. */
export default function AppLayout({ mode, onToggleMode }: Props) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const theme = useTheme()
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'))
  const [mobileOpen, setMobileOpen] = useState(false)
  const [userMenuAnchor, setUserMenuAnchor] = useState<HTMLElement | null>(null)

  const isAdmin = user?.roles.includes('ADMIN') ?? false

  // Notificações em tempo real: toast + Notification API + badge no menu.
  const { lastAlert } = useRealtime()
  const queryClient = useQueryClient()
  const [toast, setToast] = useState<AlertEvent | null>(null)

  const dashboard = useQuery<Dashboard>({
    queryKey: ['dashboard'],
    queryFn: async () => (await api.get<Dashboard>('/api/dashboard')).data,
    refetchInterval: 60_000,
  })
  const activeAlerts = dashboard.data?.activeAlerts ?? 0

  useEffect(() => {
    // Permissão para notificações nativas — melhor esforço, sem insistir.
    if ('Notification' in window && Notification.permission === 'default') {
      void Notification.requestPermission()
    }
  }, [])

  useEffect(() => {
    if (!lastAlert) return
    // Badge/listas refletem o novo estado imediatamente.
    void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    void queryClient.invalidateQueries({ queryKey: ['alerts'] })
    if (lastAlert.action === 'CREATED') {
      setToast(lastAlert)
      showNativeNotification(lastAlert.message)
    }
  }, [lastAlert, queryClient])

  const items = [
    { to: '/', label: 'Dashboard', icon: <DashboardIcon />, badge: 0 },
    { to: '/historico', label: 'Histórico', icon: <HistoryIcon />, badge: 0 },
    { to: '/clima', label: 'Clima', icon: <CloudIcon />, badge: 0 },
    { to: '/alertas', label: 'Alertas', icon: <NotificationsIcon />, badge: activeAlerts },
    ...(isAdmin ? [
      { to: '/configuracoes', label: 'Configurações', icon: <SettingsIcon />, badge: 0 },
      { to: '/usuarios', label: 'Usuários', icon: <GroupIcon />, badge: 0 },
    ] : []),
  ]

  const drawer = (
    <Box sx={{ width: DRAWER_WIDTH }} role="navigation">
      <Toolbar sx={{ gap: 1 }}>
        <BoltIcon color="warning" />
        <Typography variant="h6" noWrap>Monitor Solar</Typography>
      </Toolbar>
      <Divider />
      <List>
        {items.map(item => (
          <ListItemButton
            key={item.to}
            component={NavLink}
            to={item.to}
            selected={location.pathname === item.to}
            onClick={() => setMobileOpen(false)}
          >
            <ListItemIcon>
              <Badge badgeContent={item.badge} color="error" max={99}>
                {item.icon}
              </Badge>
            </ListItemIcon>
            <ListItemText primary={item.label} />
          </ListItemButton>
        ))}
      </List>
    </Box>
  )

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar
        position="fixed"
        color="default"
        elevation={1}
        // Acima do drawer só no desktop (drawer permanente/clipped); no
        // mobile o drawer temporário e seu backdrop devem cobrir o AppBar.
        sx={{ zIndex: isDesktop ? theme.zIndex.drawer + 1 : theme.zIndex.appBar }}
      >
        <Toolbar>
          {!isDesktop && (
            <IconButton edge="start" onClick={() => setMobileOpen(o => !o)} sx={{ mr: 1 }}>
              <MenuIcon />
            </IconButton>
          )}
          <BoltIcon sx={{ mr: 1 }} color="warning" />
          <Typography variant="h6" sx={{ flexGrow: 1 }} noWrap>
            Monitor Solar Deye
          </Typography>
          <IconButton onClick={onToggleMode} color="inherit">
            {mode === 'light' ? <DarkModeIcon /> : <LightModeIcon />}
          </IconButton>
          <Tooltip title={user?.fullName ?? ''}>
            <IconButton onClick={e => setUserMenuAnchor(e.currentTarget)} sx={{ ml: 1 }}>
              <Avatar sx={{ width: 32, height: 32, fontSize: 14 }}>
                {user?.fullName?.charAt(0).toUpperCase() ?? '?'}
              </Avatar>
            </IconButton>
          </Tooltip>
          <Menu
            anchorEl={userMenuAnchor}
            open={userMenuAnchor !== null}
            onClose={() => setUserMenuAnchor(null)}
          >
            <MenuItem disabled>
              <Typography variant="body2">{user?.username} · {user?.roles.join(', ')}</Typography>
            </MenuItem>
            <Divider />
            <MenuItem onClick={() => { setUserMenuAnchor(null); navigate('/trocar-senha') }}>
              <ListItemIcon><LockResetIcon fontSize="small" /></ListItemIcon>
              Trocar senha
            </MenuItem>
            <MenuItem onClick={() => { setUserMenuAnchor(null); void logout() }}>
              <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>
              Sair
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      {isDesktop ? (
        <Drawer variant="permanent" sx={{ width: DRAWER_WIDTH, flexShrink: 0 }}>
          {drawer}
        </Drawer>
      ) : (
        <Drawer open={mobileOpen} onClose={() => setMobileOpen(false)}>
          {drawer}
        </Drawer>
      )}

      <Box component="main" sx={{ flexGrow: 1, p: { xs: 2, md: 3 }, minWidth: 0 }}>
        <Toolbar />
        <Outlet />
      </Box>

      <Snackbar
        open={toast !== null}
        autoHideDuration={8000}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <MuiAlert
          severity={toast?.severity === 'CRITICAL' ? 'error' : 'warning'}
          onClose={() => setToast(null)}
          onClick={() => { setToast(null); navigate('/alertas') }}
          sx={{ cursor: 'pointer' }}
        >
          {toast?.message}
        </MuiAlert>
      </Snackbar>
    </Box>
  )
}
