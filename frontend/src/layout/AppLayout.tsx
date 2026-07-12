import { useState } from 'react'
import { Outlet, NavLink, useLocation } from 'react-router-dom'
import {
  AppBar, Avatar, Box, Divider, Drawer, IconButton, List, ListItemButton,
  ListItemIcon, ListItemText, Menu, MenuItem, Toolbar, Tooltip, Typography,
  useMediaQuery, useTheme,
} from '@mui/material'
import MenuIcon from '@mui/icons-material/Menu'
import BoltIcon from '@mui/icons-material/Bolt'
import DashboardIcon from '@mui/icons-material/Dashboard'
import HistoryIcon from '@mui/icons-material/History'
import NotificationsIcon from '@mui/icons-material/Notifications'
import SettingsIcon from '@mui/icons-material/Settings'
import GroupIcon from '@mui/icons-material/Group'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import LockResetIcon from '@mui/icons-material/LockReset'
import LogoutIcon from '@mui/icons-material/Logout'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const DRAWER_WIDTH = 232

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

  const items = [
    { to: '/', label: 'Dashboard', icon: <DashboardIcon /> },
    { to: '/historico', label: 'Histórico', icon: <HistoryIcon /> },
    { to: '/alertas', label: 'Alertas', icon: <NotificationsIcon /> },
    ...(isAdmin ? [
      { to: '/configuracoes', label: 'Configurações', icon: <SettingsIcon /> },
      { to: '/usuarios', label: 'Usuários', icon: <GroupIcon /> },
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
            <ListItemIcon>{item.icon}</ListItemIcon>
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
    </Box>
  )
}
