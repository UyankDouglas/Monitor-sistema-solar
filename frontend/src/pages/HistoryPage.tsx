import { useMemo, useState } from 'react'
import {
  Alert as MuiAlert, Box, Button, ButtonGroup, Card, CardContent, Snackbar,
  Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  TextField, Typography,
} from '@mui/material'
import DownloadIcon from '@mui/icons-material/Download'
import { useQuery } from '@tanstack/react-query'
import ReactECharts from 'echarts-for-react'
import axios from 'axios'
import { api } from '../api/client'
import type { DailyGeneration, Dashboard, EnergySeries, Statistics } from '../api/types'
import { downloadFile } from '../lib/download'
import { fmtDate, fmtKg, fmtKwh, fmtMoney, fmtPct, toIsoDate } from '../lib/format'
import GenerationBars from '../components/GenerationBars'

type Preset = 'dia' | 'semana' | 'mes' | 'ano' | 'personalizado'

interface Props {
  mode: 'light' | 'dark'
}

/** Histórico com filtros de período, gráficos, tabela e exportação. */
export default function HistoryPage({ mode }: Props) {
  const todayIso = toIsoDate(new Date())
  const [preset, setPreset] = useState<Preset>('semana')
  const [customFrom, setCustomFrom] = useState(toIsoDate(new Date(Date.now() - 6 * 86_400_000)))
  const [customTo, setCustomTo] = useState(todayIso)

  const { from, to } = useMemo(() => {
    const now = new Date()
    switch (preset) {
      case 'dia':
        return { from: todayIso, to: todayIso }
      case 'semana':
        return { from: toIsoDate(new Date(now.getTime() - 6 * 86_400_000)), to: todayIso }
      case 'mes':
        return { from: toIsoDate(new Date(now.getFullYear(), now.getMonth(), 1)), to: todayIso }
      case 'ano':
        return { from: toIsoDate(new Date(now.getFullYear(), 0, 1)), to: todayIso }
      case 'personalizado':
        return { from: customFrom, to: customTo }
    }
  }, [preset, customFrom, customTo, todayIso])

  const validRange = from <= to

  const daily = useQuery<DailyGeneration[]>({
    queryKey: ['history-daily', from, to],
    queryFn: async () =>
      (await api.get<DailyGeneration[]>('/api/energy/daily', { params: { from, to } })).data,
    enabled: validRange,
  })

  const stats = useQuery<Statistics>({
    queryKey: ['history-stats', from, to],
    queryFn: async () =>
      (await api.get<Statistics>('/api/statistics', { params: { from, to } })).data,
    enabled: validRange,
  })

  // Curva intradiária quando o período é um único dia.
  const singleDay = from === to
  const intraday = useQuery<EnergySeries>({
    queryKey: ['history-intraday', from],
    queryFn: async () => (await api.get<EnergySeries>('/api/energy/history', {
      params: {
        from: `${from}T00:00:00-03:00`,
        to: `${from}T23:59:59-03:00`,
      },
    })).data,
    enabled: validRange && singleDay,
  })

  // Moeda configurável: o dashboard já a expõe (config > planta).
  const dash = useQuery<Dashboard>({
    queryKey: ['dashboard'],
    queryFn: async () => (await api.get<Dashboard>('/api/dashboard')).data,
    staleTime: 60_000,
  })
  const currency = dash.data?.currency ?? 'BRL'

  const [exporting, setExporting] = useState<string | null>(null)
  const [exportError, setExportError] = useState<string | null>(null)
  async function exportAs(format: 'csv' | 'xlsx' | 'pdf') {
    setExporting(format)
    setExportError(null)
    try {
      await downloadFile('/api/energy/daily/export', { from, to, format })
    } catch (err) {
      setExportError(await extractErrorDetail(err)
        ?? 'Falha ao exportar. Verifique a conexão e tente novamente.')
    } finally {
      setExporting(null)
    }
  }

  const rows = daily.data ?? []
  const s = stats.data

  return (
    <Stack spacing={3}>
      <Typography variant="h5">Histórico</Typography>

      {/* Filtros */}
      <Card>
        <CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ md: 'center' }}>
            <ButtonGroup>
              {(['dia', 'semana', 'mes', 'ano', 'personalizado'] as Preset[]).map(p => (
                <Button
                  key={p}
                  variant={preset === p ? 'contained' : 'outlined'}
                  onClick={() => setPreset(p)}
                >
                  {p === 'mes' ? 'Mês' : p.charAt(0).toUpperCase() + p.slice(1)}
                </Button>
              ))}
            </ButtonGroup>
            {preset === 'personalizado' && (
              <Stack direction="row" spacing={1}>
                <TextField type="date" size="small" label="De" value={customFrom}
                  onChange={e => setCustomFrom(e.target.value)}
                  slotProps={{ inputLabel: { shrink: true } }} />
                <TextField type="date" size="small" label="Até" value={customTo}
                  onChange={e => setCustomTo(e.target.value)}
                  slotProps={{ inputLabel: { shrink: true } }} />
              </Stack>
            )}
            <Box sx={{ flexGrow: 1 }} />
            <ButtonGroup variant="outlined">
              {(['csv', 'xlsx', 'pdf'] as const).map(f => (
                <Button
                  key={f}
                  startIcon={<DownloadIcon />}
                  disabled={!validRange || exporting !== null}
                  onClick={() => void exportAs(f)}
                >
                  {exporting === f ? '…' : f.toUpperCase()}
                </Button>
              ))}
            </ButtonGroup>
          </Stack>
          {!validRange && (
            <Typography color="error" variant="caption">
              Período inválido: a data inicial deve ser anterior à final.
            </Typography>
          )}
        </CardContent>
      </Card>

      {/* Estatísticas do período */}
      {s && (
        <Box sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' },
        }}>
          <MiniStat label="Geração no período" value={fmtKwh(s.totalEnergyKwh)} />
          <MiniStat label="Economia" value={fmtMoney(s.totalSavings, currency)} />
          <MiniStat label="CO₂ evitado" value={fmtKg(s.totalCo2AvoidedKg)} />
          <MiniStat label="Média diária" value={fmtKwh(s.avgDailyKwh)} />
          <MiniStat label="Melhor dia"
            value={s.bestDay ? `${fmtKwh(s.bestDay.energyKwh)} (${fmtDate(s.bestDay.date)})` : '—'} />
          <MiniStat label="Pior dia"
            value={s.worstDay ? `${fmtKwh(s.worstDay.energyKwh)} (${fmtDate(s.worstDay.date)})` : '—'} />
          <MiniStat label="Maior pico"
            value={s.maxPeak ? `${s.maxPeak.powerW} W (${fmtDate(s.maxPeak.date)})` : '—'} />
          <MiniStat label="Fator de capacidade" value={fmtPct(s.capacityFactorPct)} />
        </Box>
      )}

      {/* Gráfico do período */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            {singleDay ? `Potência em ${fmtDate(from)}` : 'Geração por dia'}
          </Typography>
          {singleDay ? (
            <IntradayChart data={intraday.data} mode={mode} />
          ) : (
            <GenerationBars
              mode={mode}
              seriesName="Geração"
              categories={rows.map(r => fmtDate(r.date).slice(0, 5))}
              values={rows.map(r => r.energyKwh)}
              height={320}
            />
          )}
        </CardContent>
      </Card>

      {/* Tabela detalhada */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>Detalhamento diário</Typography>
          <TableContainer sx={{ maxHeight: 440 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell>Data</TableCell>
                  <TableCell align="right">Geração</TableCell>
                  <TableCell align="right">Pico</TableCell>
                  <TableCell align="right">Consumo</TableCell>
                  <TableCell align="right">Exportado</TableCell>
                  <TableCell align="right">Importado</TableCell>
                  <TableCell align="right">Autossuf.</TableCell>
                  <TableCell align="right">Economia</TableCell>
                  <TableCell align="right">CO₂</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={9}>
                      <Typography color="text.secondary">
                        {daily.isLoading ? 'Carregando…' : 'Sem dados consolidados no período.'}
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
                {rows.map(r => (
                  <TableRow key={r.date} hover>
                    <TableCell>{fmtDate(r.date)}</TableCell>
                    <TableCell align="right">{fmtKwh(r.energyKwh)}</TableCell>
                    <TableCell align="right">{r.peakPowerW == null ? '—' : `${r.peakPowerW} W`}</TableCell>
                    <TableCell align="right">{fmtKwh(r.consumptionKwh)}</TableCell>
                    <TableCell align="right">{fmtKwh(r.exportKwh)}</TableCell>
                    <TableCell align="right">{fmtKwh(r.importKwh)}</TableCell>
                    <TableCell align="right">{fmtPct(r.selfSufficiencyPct)}</TableCell>
                    <TableCell align="right">{fmtMoney(r.savings, currency)}</TableCell>
                    <TableCell align="right">{fmtKg(r.co2AvoidedKg)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      <Snackbar
        open={exportError !== null}
        autoHideDuration={6000}
        onClose={() => setExportError(null)}
      >
        <MuiAlert severity="error" onClose={() => setExportError(null)}>
          {exportError}
        </MuiAlert>
      </Snackbar>
    </Stack>
  )
}

/** Extrai o detail do ProblemDetail mesmo quando a resposta veio como Blob. */
async function extractErrorDetail(err: unknown): Promise<string | null> {
  if (!axios.isAxiosError(err)) return null
  const data: unknown = err.response?.data
  try {
    if (data instanceof Blob) {
      const parsed = JSON.parse(await data.text()) as { detail?: string }
      return parsed.detail ?? null
    }
    return (data as { detail?: string } | undefined)?.detail ?? null
  } catch {
    return null
  }
}

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
        <Typography variant="caption" color="text.secondary">{label}</Typography>
        <Typography variant="subtitle1">{value}</Typography>
      </CardContent>
    </Card>
  )
}

function IntradayChart({ data, mode }: { data: EnergySeries | undefined; mode: 'light' | 'dark' }) {
  const option = {
    animation: false,
    tooltip: { trigger: 'axis' },
    legend: { data: ['Geração', 'Consumo'] },
    grid: { left: 56, right: 16, top: 40, bottom: 28 },
    xAxis: {
      type: 'time',
      axisLabel: {
        formatter: (ms: number) =>
          new Date(ms).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
      },
    },
    yAxis: { type: 'value', name: 'W', min: 0 },
    series: [
      {
        name: 'Geração', type: 'line', smooth: true, showSymbol: false,
        areaStyle: { opacity: 0.25 }, color: '#f6b93b',
        data: (data?.points ?? []).map(p => [Date.parse(p.timestamp), p.acPowerW]),
      },
      {
        name: 'Consumo', type: 'line', smooth: true, showSymbol: false,
        color: mode === 'dark' ? '#82ccdd' : '#1976d2',
        data: (data?.points ?? []).map(p => [Date.parse(p.timestamp), p.loadPowerW]),
      },
    ],
  }
  return (
    <Box sx={{ height: 320 }}>
      <ReactECharts option={option} notMerge style={{ height: '100%', width: '100%' }}
        theme={mode === 'dark' ? 'dark' : undefined} />
    </Box>
  )
}
