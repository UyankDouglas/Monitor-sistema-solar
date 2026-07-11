import { useMemo, useState } from 'react'
import {
  AppBar, Box, Card, CardContent, Chip, Container, CssBaseline,
  IconButton, Stack, ThemeProvider, Toolbar, Typography, createTheme,
} from '@mui/material'
import DarkModeIcon from '@mui/icons-material/DarkMode'
import LightModeIcon from '@mui/icons-material/LightMode'
import BoltIcon from '@mui/icons-material/Bolt'
import { useQuery } from '@tanstack/react-query'
import ReactECharts from 'echarts-for-react'
import { api } from './api/client'

interface Ping {
  status: string
  service: string
  timestamp: string
}

export default function App() {
  const [mode, setMode] = useState<'light' | 'dark'>('light')
  const theme = useMemo(() => createTheme({ palette: { mode } }), [mode])

  const ping = useQuery<Ping>({
    queryKey: ['ping'],
    queryFn: async () => (await api.get<Ping>('/api/ping')).data,
    retry: false,
  })

  // Amostra ilustrativa; na Etapa 10 vira a curva de potência em tempo real.
  const chartOption = {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['06h', '08h', '10h', '12h', '14h', '16h', '18h'] },
    yAxis: { type: 'value', name: 'kW' },
    series: [{ name: 'Potência', type: 'line', smooth: true, areaStyle: {}, data: [0.2, 2.1, 5.8, 8.9, 7.4, 3.6, 0.5] }],
  }

  const backendOnline = ping.data?.status === 'UP'

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <AppBar position="static" color="default" elevation={1}>
        <Toolbar>
          <BoltIcon sx={{ mr: 1 }} color="warning" />
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            Monitor Solar Deye
          </Typography>
          <IconButton onClick={() => setMode(m => (m === 'light' ? 'dark' : 'light'))} color="inherit">
            {mode === 'light' ? <DarkModeIcon /> : <LightModeIcon />}
          </IconButton>
        </Toolbar>
      </AppBar>

      <Container maxWidth="md" sx={{ py: 4 }}>
        <Stack spacing={3}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Status da stack</Typography>
              <Stack direction="row" spacing={1} alignItems="center">
                <Chip
                  label={backendOnline ? 'Backend online' : ping.isLoading ? 'Verificando...' : 'Backend offline'}
                  color={backendOnline ? 'success' : ping.isLoading ? 'default' : 'error'}
                />
                {ping.data && (
                  <Typography variant="body2" color="text.secondary">
                    {ping.data.service} · {new Date(ping.data.timestamp).toLocaleTimeString('pt-BR')}
                  </Typography>
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
    </ThemeProvider>
  )
}
