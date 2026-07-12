import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import ReactECharts from 'echarts-for-react'
import { Box, Chip, Stack, Typography } from '@mui/material'
import { api } from '../api/client'
import type { ReadingEvent } from '../realtime/types'

/** Janela deslizante exibida no gráfico. */
const WINDOW_MINUTES = 15

interface HistorySeries {
  bucketSeconds: number
  points: Array<{
    timestamp: string
    acPowerW: number | null
    loadPowerW: number | null
    batterySocPct: number | null
  }>
}

interface ChartPoint {
  t: number
  ac: number | null
  load: number | null
}

interface Props {
  connected: boolean
  latest: ReadingEvent | null
  mode: 'light' | 'dark'
}

/**
 * Potência em tempo real: semeia com os últimos {@link WINDOW_MINUTES} min
 * do histórico e anexa cada leitura que chega pelo WebSocket, descartando o
 * que sai da janela.
 */
export default function LivePowerChart({ connected, latest, mode }: Props) {
  const [points, setPoints] = useState<ChartPoint[]>([])
  const seededRef = useRef(false)

  // Janela inicial calculada uma única vez (não a cada render).
  const [historyRange] = useState(() => {
    const now = Date.now()
    return {
      from: new Date(now - WINDOW_MINUTES * 60_000).toISOString(),
      to: new Date(now + 60_000).toISOString(),
    }
  })

  const history = useQuery<HistorySeries>({
    queryKey: ['history-seed', historyRange.from],
    queryFn: async () =>
      (await api.get<HistorySeries>('/api/energy/history', { params: historyRange })).data,
    staleTime: Infinity,
  })

  // Semeadura única a partir do histórico persistido.
  useEffect(() => {
    if (seededRef.current || !history.data) return
    seededRef.current = true
    setPoints(prev => {
      const seed: ChartPoint[] = history.data.points.map(p => ({
        t: Date.parse(p.timestamp),
        ac: p.acPowerW,
        load: p.loadPowerW,
      }))
      // Leituras ao vivo que chegaram antes do histórico continuam no fim.
      const merged = [...seed, ...prev.filter(p => seed.length === 0 || p.t > seed[seed.length - 1].t)]
      return merged
    })
  }, [history.data])

  // Anexa cada leitura ao vivo e apara a janela.
  useEffect(() => {
    if (!latest) return
    const t = Date.parse(latest.reading.sampledAt)
    if (Number.isNaN(t)) return
    setPoints(prev => {
      if (prev.length > 0 && prev[prev.length - 1].t >= t) return prev // duplicada/fora de ordem
      // Janela ancorada no relógio do SERVIDOR (timestamp do próprio evento):
      // skew entre cliente e servidor não pode cortar pontos válidos.
      const cutoff = t - WINDOW_MINUTES * 60_000
      return [
        ...prev.filter(p => p.t >= cutoff),
        { t, ac: latest.reading.acPowerW, load: latest.reading.loadPowerW },
      ]
    })
  }, [latest])

  const option = useMemo(() => ({
    animation: false,
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: unknown) => (value == null ? '—' : `${value} W`),
    },
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
        name: 'Geração',
        type: 'line',
        smooth: true,
        showSymbol: false,
        areaStyle: { opacity: 0.25 },
        color: '#f6b93b',
        data: points.map(p => [p.t, p.ac]),
      },
      {
        name: 'Consumo',
        type: 'line',
        smooth: true,
        showSymbol: false,
        color: mode === 'dark' ? '#82ccdd' : '#1976d2',
        data: points.map(p => [p.t, p.load]),
      },
    ],
  }), [points, mode])

  return (
    <>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
        <Typography variant="h6" sx={{ flexGrow: 1 }}>
          Potência em tempo real
        </Typography>
        <Chip
          size="small"
          label={connected ? 'AO VIVO' : 'RECONECTANDO…'}
          color={connected ? 'success' : 'warning'}
          variant={connected ? 'filled' : 'outlined'}
        />
      </Stack>
      <Box sx={{ height: 320 }}>
        <ReactECharts
          option={option}
          notMerge
          style={{ height: '100%', width: '100%' }}
          theme={mode === 'dark' ? 'dark' : undefined}
          opts={{ renderer: 'canvas' }}
        />
      </Box>
      {history.isLoading && (
        <Typography variant="caption" color="text.secondary">
          Carregando histórico recente…
        </Typography>
      )}
    </>
  )
}
