import {
  Alert as MuiAlert, Box, Button, Card, CardContent, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material'
import RefreshIcon from '@mui/icons-material/Refresh'
import CloudIcon from '@mui/icons-material/Cloud'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import ReactECharts from 'echarts-for-react'
import { api } from '../api/client'
import { fmtDate, fmtDateTime, fmtKwh } from '../lib/format'
import { useAuth } from '../auth/AuthContext'

interface WeatherSummary {
  available: boolean
  reason: string | null
  current: {
    observedAt: string
    temperatureC: number | null
    cloudCoverPct: number | null
    condition: string | null
  } | null
  days: Array<{
    date: string
    condition: string | null
    tempMaxC: number | null
    cloudCoverPct: number | null
    expectedKwh: number | null
    actualKwh: number | null
    deviationPct: number | null
  }>
}

interface Props {
  mode: 'light' | 'dark'
}

/** Previsão do tempo × geração real (Open-Meteo, sem chave de API). */
export default function WeatherPage({ mode }: Props) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const isAdmin = user?.roles.includes('ADMIN') ?? false

  const summary = useQuery<WeatherSummary>({
    queryKey: ['weather'],
    queryFn: async () => (await api.get<WeatherSummary>('/api/weather/summary')).data,
    refetchInterval: 10 * 60_000,
  })

  const refresh = useMutation({
    mutationFn: async () => (await api.post<WeatherSummary>('/api/weather/refresh')).data,
    onSuccess: data => queryClient.setQueryData(['weather'], data),
  })

  const s = summary.data

  const chartOption = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['Previsto', 'Real'] },
    grid: { left: 56, right: 16, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: (s?.days ?? []).map(d => fmtDate(d.date).slice(0, 5)) },
    yAxis: { type: 'value', name: 'kWh' },
    series: [
      {
        name: 'Previsto', type: 'bar', barMaxWidth: 22,
        itemStyle: { borderRadius: [4, 4, 0, 0], opacity: 0.55 },
        color: '#8395a7',
        data: (s?.days ?? []).map(d => d.expectedKwh),
      },
      {
        name: 'Real', type: 'bar', barMaxWidth: 22,
        itemStyle: { borderRadius: [4, 4, 0, 0] },
        color: '#f6b93b',
        data: (s?.days ?? []).map(d => d.actualKwh),
      },
    ],
  }

  return (
    <Stack spacing={3}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Clima — previsto × real</Typography>
        {isAdmin && (
          <Button
            startIcon={<RefreshIcon />}
            disabled={refresh.isPending || !s?.available}
            onClick={() => refresh.mutate()}
          >
            Atualizar agora
          </Button>
        )}
      </Stack>

      {summary.isError && !s && (
        <MuiAlert severity="error">
          Não foi possível carregar os dados de clima. Nova tentativa automática em instantes.
        </MuiAlert>
      )}
      {refresh.isError && (
        <MuiAlert severity="error" onClose={() => refresh.reset()}>
          Falha ao atualizar agora — o serviço Open-Meteo pode estar indisponível. Tente novamente.
        </MuiAlert>
      )}

      {s && !s.available && (
        <MuiAlert severity="info">
          {s.reason}
          {isAdmin && ' — acesse Configurações → Clima e informe as coordenadas da usina.'}
        </MuiAlert>
      )}

      {s?.current && (
        <Card>
          <CardContent>
            <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
              <CloudIcon color="info" fontSize="large" />
              <Typography variant="h6">{s.current.condition ?? '—'}</Typography>
              <Typography>🌡️ {s.current.temperatureC ?? '—'} °C</Typography>
              <Typography>☁️ {s.current.cloudCoverPct ?? '—'}% de nuvens</Typography>
              <Typography variant="caption" color="text.secondary">
                observado em {fmtDateTime(s.current.observedAt)}
              </Typography>
            </Stack>
          </CardContent>
        </Card>
      )}

      {s?.available && (
        <>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Geração prevista × real (kWh/dia)
              </Typography>
              <Box sx={{ height: 320 }}>
                <ReactECharts option={chartOption} notMerge
                  style={{ height: '100%', width: '100%' }}
                  theme={mode === 'dark' ? 'dark' : undefined} />
              </Box>
              <Typography variant="caption" color="text.secondary">
                Previsão: radiação solar da Open-Meteo × capacidade instalada × PR 0,80.
                Dias futuros ainda não têm barra "Real".
              </Typography>
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Detalhamento</Typography>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Data</TableCell>
                      <TableCell>Condição</TableCell>
                      <TableCell align="right">Máx.</TableCell>
                      <TableCell align="right">Nuvens</TableCell>
                      <TableCell align="right">Previsto</TableCell>
                      <TableCell align="right">Real</TableCell>
                      <TableCell align="right">Desvio</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(s.days ?? []).map(d => (
                      <TableRow key={d.date} hover>
                        <TableCell>{fmtDate(d.date)}</TableCell>
                        <TableCell>{d.condition ?? '—'}</TableCell>
                        <TableCell align="right">{d.tempMaxC ?? '—'} °C</TableCell>
                        <TableCell align="right">{d.cloudCoverPct ?? '—'}%</TableCell>
                        <TableCell align="right">{fmtKwh(d.expectedKwh)}</TableCell>
                        <TableCell align="right">{fmtKwh(d.actualKwh)}</TableCell>
                        <TableCell align="right" sx={{
                          color: d.deviationPct == null ? undefined
                            : d.deviationPct >= 0 ? 'success.main' : 'error.main',
                        }}>
                          {d.deviationPct == null ? '—'
                            : `${d.deviationPct > 0 ? '+' : ''}${d.deviationPct}%`}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </>
      )}
    </Stack>
  )
}
