import {
  AppBar, Card, CardContent, Chip, Container, IconButton, Stack,
  Toolbar, Tooltip, Typography,
} from '@mui/material'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import LogoutIcon from '@mui/icons-material/Logout'
import BoltIcon from '@mui/icons-material/Bolt'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useLiveReadings } from '../realtime/useLiveReadings'
import LivePowerChart from '../components/LivePowerChart'

interface Dashboard {
  inverterName: string
  inverterStatus: string
  currentPowerW: number | null
  batterySocPct: number | null
  todayEnergyKwh: number | null
  todaySavings: number | null
  currency: string
}

interface Props {
  mode: 'light' | 'dark'
  onToggleMode: () => void
}

/**
 * Página inicial pós-login. Cards priorizam a leitura mais recente do
 * WebSocket; o polling de 30 s do dashboard fica como fallback (e traz o
 * que o WS não publica, como economia consolidada).
 */
export default function HomePage({ mode, onToggleMode }: Props) {
  const { user, logout } = useAuth()
  const { connected, latest } = useLiveReadings()

  const dashboard = useQuery<Dashboard>({
    queryKey: ['dashboard'],
    queryFn: async () => (await api.get<Dashboard>('/api/dashboard')).data,
    refetchInterval: 30_000,
  })

  const d = dashboard.data

  // Tempo real vence o snapshot, mas só se conectado E fresco (< 60 s):
  // leitura obsoleta não pode mascarar o fallback de polling — ex.: card
  // "kWh hoje" preso no valor de ontem após uma queda do WS na virada do
  // dia. O re-render do polling de 30 s reavalia a idade.
  const FRESH_MS = 60_000
  const liveRaw = latest?.reading
  const live = connected && liveRaw != null
      && Date.now() - Date.parse(liveRaw.sampledAt) < FRESH_MS
    ? liveRaw
    : undefined

  const powerW = live?.acPowerW ?? d?.currentPowerW
  const socPct = live?.batterySocPct ?? d?.batterySocPct
  const todayKwh = live?.dailyEnergyKwh ?? d?.todayEnergyKwh
  const status = live?.status ?? d?.inverterStatus
  const online = status === 'ONLINE'

  return (
    <>
      <AppBar position="static" color="default" elevation={1}>
        <Toolbar>
          <BoltIcon sx={{ mr: 1 }} color="warning" />
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            Monitor Solar Deye
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mr: 2 }}>
            {user?.fullName}
          </Typography>
          <IconButton onClick={onToggleMode} color="inherit">
            {mode === 'light' ? <DarkModeIcon /> : <LightModeIcon />}
          </IconButton>
          <Tooltip title="Sair">
            <IconButton onClick={() => void logout()} color="inherit">
              <LogoutIcon />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Container maxWidth="md" sx={{ py: 4 }}>
        <Stack spacing={3}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                {d?.inverterName ?? 'Inversor'}
              </Typography>
              <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
                <Chip
                  label={status ?? 'Carregando…'}
                  color={online ? 'success' : 'default'}
                />
                <Typography variant="body2">⚡ {powerW ?? '—'} W agora</Typography>
                <Typography variant="body2">🔋 SOC {socPct ?? '—'}%</Typography>
                <Typography variant="body2">☀️ {todayKwh ?? '—'} kWh hoje</Typography>
                <Typography variant="body2">
                  💰 {d?.todaySavings != null ? `${d.currency} ${d.todaySavings}` : '—'} hoje
                </Typography>
              </Stack>
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <LivePowerChart connected={connected} latest={latest} mode={mode} />
            </CardContent>
          </Card>
        </Stack>
      </Container>
    </>
  )
}
