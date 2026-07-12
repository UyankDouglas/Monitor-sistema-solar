import {
  AppBar, Box, Card, CardContent, Chip, Container, IconButton, Stack,
  Toolbar, Tooltip, Typography,
} from '@mui/material'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import LogoutIcon from '@mui/icons-material/Logout'
import BoltIcon from '@mui/icons-material/Bolt'
import { useQuery } from '@tanstack/react-query'
import ReactECharts from 'echarts-for-react'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'

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
 * Página inicial pós-login: resumo do dashboard (a tela completa com todos
 * os cards e gráficos em tempo real é a Etapa 10).
 */
export default function HomePage({ mode, onToggleMode }: Props) {
  const { user, logout } = useAuth()

  const dashboard = useQuery<Dashboard>({
    queryKey: ['dashboard'],
    queryFn: async () => (await api.get<Dashboard>('/api/dashboard')).data,
    refetchInterval: 10_000,
  })

  const chartOption = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['06h', '08h', '10h', '12h', '14h', '16h', '18h'] },
    yAxis: { type: 'value', name: 'kW' },
    series: [{ name: 'Potência', type: 'line', smooth: true, areaStyle: {}, data: [0.2, 2.1, 5.8, 8.9, 7.4, 3.6, 0.5] }],
  }

  const d = dashboard.data
  const online = d?.inverterStatus === 'ONLINE'

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
                  label={online ? 'ONLINE' : d?.inverterStatus ?? 'Carregando…'}
                  color={online ? 'success' : 'default'}
                />
                {d && (
                  <>
                    <Typography variant="body2">⚡ {d.currentPowerW ?? '—'} W agora</Typography>
                    <Typography variant="body2">🔋 SOC {d.batterySocPct ?? '—'}%</Typography>
                    <Typography variant="body2">☀️ {d.todayEnergyKwh ?? '—'} kWh hoje</Typography>
                    <Typography variant="body2">
                      💰 {d.todaySavings != null ? `${d.currency} ${d.todaySavings}` : '—'} hoje
                    </Typography>
                  </>
                )}
              </Stack>
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Geração (amostra)</Typography>
              <Box sx={{ height: 320 }}>
                <ReactECharts option={chartOption} style={{ height: '100%', width: '100%' }} />
              </Box>
            </CardContent>
          </Card>
        </Stack>
      </Container>
    </>
  )
}
