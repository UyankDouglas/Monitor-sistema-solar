import { Box, Card, CardContent, Chip, Stack, Typography } from '@mui/material'
import BoltIcon from '@mui/icons-material/Bolt'
import WbSunnyIcon from '@mui/icons-material/WbSunny'
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth'
import FunctionsIcon from '@mui/icons-material/Functions'
import SavingsIcon from '@mui/icons-material/Savings'
import Co2Icon from '@mui/icons-material/Co2'
import HomeIcon from '@mui/icons-material/Home'
import BatteryChargingFullIcon from '@mui/icons-material/BatteryChargingFull'
import DeviceThermostatIcon from '@mui/icons-material/DeviceThermostat'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import type { CurrentEnergy, DailyGeneration, Dashboard, MonthlyGeneration } from '../api/types'
import { fmtDateTime, fmtKg, fmtKwh, fmtMoney, fmtPct, fmtW, toIsoDate } from '../lib/format'
import { useRealtime } from '../realtime/RealtimeContext'
import LivePowerChart from '../components/LivePowerChart'
import StatCard from '../components/StatCard'
import EnergyFlowCard from '../components/EnergyFlowCard'
import GenerationBars from '../components/GenerationBars'

const MONTH_LABELS = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez']

interface Props {
  mode: 'light' | 'dark'
}

export default function DashboardPage({ mode }: Props) {
  const { connected, latest } = useRealtime()

  const dashboard = useQuery<Dashboard>({
    queryKey: ['dashboard'],
    queryFn: async () => (await api.get<Dashboard>('/api/dashboard')).data,
    refetchInterval: 30_000,
  })

  const current = useQuery<CurrentEnergy>({
    queryKey: ['energy-current'],
    queryFn: async () => (await api.get<CurrentEnergy>('/api/energy/current')).data,
    refetchInterval: 15_000,
    retry: false,
  })

  const today = new Date()
  const daily = useQuery<DailyGeneration[]>({
    queryKey: ['daily-30d'],
    queryFn: async () => (await api.get<DailyGeneration[]>('/api/energy/daily', {
      params: {
        from: toIsoDate(new Date(today.getTime() - 29 * 86_400_000)),
        to: toIsoDate(today),
      },
    })).data,
    refetchInterval: 5 * 60_000,
  })

  const monthly = useQuery<MonthlyGeneration[]>({
    queryKey: ['monthly-year', today.getFullYear()],
    queryFn: async () => (await api.get<MonthlyGeneration[]>('/api/energy/monthly', {
      params: { year: today.getFullYear() },
    })).data,
    refetchInterval: 10 * 60_000,
  })

  const d = dashboard.data

  // Leitura ao vivo (fresca) vence o snapshot do polling.
  const FRESH_MS = 60_000
  const liveRaw = latest?.reading
  const live = connected && liveRaw != null
      && Date.now() - Date.parse(liveRaw.sampledAt) < FRESH_MS
    ? liveRaw
    : undefined

  const powerW = live?.acPowerW ?? d?.currentPowerW
  const loadW = live?.loadPowerW ?? d?.loadPowerW
  const exportW = live?.exportPowerW ?? d?.exportPowerW
  const importW = live?.importPowerW ?? d?.importPowerW
  const batteryW = live?.batteryPowerW ?? d?.batteryPowerW
  const socPct = live?.batterySocPct ?? d?.batterySocPct
  const todayKwh = live?.dailyEnergyKwh ?? d?.todayEnergyKwh
  const status = live?.status ?? d?.inverterStatus
  const online = status === 'ONLINE'
  const mppt = live?.mpptReadings ?? current.data?.mppt ?? []

  return (
    <Stack spacing={3}>
      {/* Cabeçalho do inversor */}
      <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
        <Typography variant="h5">{d?.inverterName ?? 'Inversor'}</Typography>
        <Chip label={status ?? '…'} color={online ? 'success' : status ? 'error' : 'default'} size="small" />
        {d?.activeAlerts != null && d.activeAlerts > 0 && (
          <Chip label={`${d.activeAlerts} alerta(s) ativo(s)`} color="warning" size="small" />
        )}
        <Typography variant="caption" color="text.secondary">
          última leitura: {fmtDateTime(live?.sampledAt ?? d?.sampledAt)}
        </Typography>
      </Stack>

      {/* Grid de indicadores */}
      <Box sx={{
        display: 'grid',
        gap: 2,
        gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)', lg: 'repeat(6, 1fr)' },
      }}>
        <StatCard icon={<BoltIcon />} label="Potência agora" value={fmtW(powerW)} color="#f6b93b" />
        <StatCard icon={<WbSunnyIcon />} label="Energia hoje" value={fmtKwh(todayKwh)}
          hint={fmtMoney(d?.todaySavings, d?.currency)} color="#e58e26" />
        <StatCard icon={<CalendarMonthIcon />} label="Energia no mês" value={fmtKwh(d?.monthEnergyKwh)}
          hint={fmtMoney(d?.monthSavings, d?.currency)} />
        <StatCard icon={<FunctionsIcon />} label="Energia total" value={fmtKwh(d?.totalEnergyKwh)} />
        <StatCard icon={<SavingsIcon />} label="Economia total" value={fmtMoney(d?.totalSavingsEstimate, d?.currency)}
          hint="estimativa" color="#27ae60" />
        <StatCard icon={<Co2Icon />} label="CO₂ evitado" value={fmtKg(d?.totalCo2AvoidedKgEstimate)}
          hint="desde o início" color="#16a085" />
        <StatCard icon={<HomeIcon />} label="Consumo da casa" value={fmtW(loadW)} />
        <StatCard icon={<BoltIcon />} label="Exportando" value={fmtW(exportW)} color="#27ae60" />
        <StatCard icon={<BoltIcon />} label="Importando" value={fmtW(importW)} color="#c0392b" />
        <StatCard icon={<BatteryChargingFullIcon />} label="Bateria (SOC)" value={fmtPct(socPct)}
          hint={batteryW == null ? undefined : batteryW > 0 ? 'descarregando' : batteryW < 0 ? 'carregando' : 'repouso'}
          color="#2980b9" />
        <StatCard icon={<DeviceThermostatIcon />} label="Temp. inversor"
          value={d?.inverterTemperatureC == null ? '—' : `${d.inverterTemperatureC} °C`} />
        <StatCard icon={<DeviceThermostatIcon />} label="Temp. bateria"
          value={d?.batteryTemperatureC == null ? '—' : `${d.batteryTemperatureC} °C`} />
      </Box>

      {/* Tempo real + fluxo */}
      <Box sx={{
        display: 'grid',
        gap: 2,
        gridTemplateColumns: { xs: '1fr', lg: '2fr 1fr' },
        alignItems: 'stretch',
      }}>
        <Card>
          <CardContent>
            <LivePowerChart connected={connected} latest={latest} mode={mode} />
          </CardContent>
        </Card>
        <EnergyFlowCard solarW={powerW} loadW={loadW} batteryW={batteryW}
          exportW={exportW} importW={importW} />
      </Box>

      {/* MPPT + rede */}
      <Box sx={{
        display: 'grid',
        gap: 2,
        gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
      }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>Strings MPPT</Typography>
            {mppt.length === 0 ? (
              <Typography color="text.secondary">Sem leituras ainda.</Typography>
            ) : (
              <Stack spacing={1}>
                {mppt.map(s => (
                  <Stack key={s.stringIndex} direction="row" spacing={2} alignItems="baseline">
                    <Chip label={`MPPT ${s.stringIndex}`} size="small" />
                    <Typography variant="h6">{fmtW(s.powerW)}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {s.voltage ?? '—'} V · {s.currentA ?? '—'} A
                    </Typography>
                  </Stack>
                ))}
              </Stack>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>Rede elétrica</Typography>
            <Stack direction="row" spacing={3} flexWrap="wrap" useFlexGap>
              <GridPhase label="L1" volts={d?.gridVoltageL1} />
              <GridPhase label="L2" volts={d?.gridVoltageL2} />
              <GridPhase label="L3" volts={d?.gridVoltageL3} />
              <Stack>
                <Typography variant="caption" color="text.secondary">Frequência</Typography>
                <Typography variant="h6">
                  {d?.gridFrequencyHz == null ? '—' : `${d.gridFrequencyHz} Hz`}
                </Typography>
              </Stack>
            </Stack>
          </CardContent>
        </Card>
      </Box>

      {/* Barras: diário 30d + mensal do ano */}
      <Box sx={{
        display: 'grid',
        gap: 2,
        gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' },
      }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>Geração diária — últimos 30 dias</Typography>
            <GenerationBars
              mode={mode}
              seriesName="Geração"
              categories={(daily.data ?? []).map(g => g.date.slice(8, 10) + '/' + g.date.slice(5, 7))}
              values={(daily.data ?? []).map(g => g.energyKwh)}
            />
          </CardContent>
        </Card>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>Geração mensal — {today.getFullYear()}</Typography>
            <GenerationBars
              mode={mode}
              seriesName="Geração"
              color="#e58e26"
              categories={(monthly.data ?? []).map(m => MONTH_LABELS[m.month - 1])}
              values={(monthly.data ?? []).map(m => m.energyKwh)}
            />
          </CardContent>
        </Card>
      </Box>
    </Stack>
  )
}

function GridPhase({ label, volts }: { label: string; volts: number | null | undefined }) {
  return (
    <Stack>
      <Typography variant="caption" color="text.secondary">Tensão {label}</Typography>
      <Typography variant="h6">{volts == null ? '—' : `${volts} V`}</Typography>
    </Stack>
  )
}
