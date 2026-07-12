import { Card, CardContent, Divider, Stack, Typography } from '@mui/material'
import WbSunnyIcon from '@mui/icons-material/WbSunny'
import HomeIcon from '@mui/icons-material/Home'
import BatteryChargingFullIcon from '@mui/icons-material/BatteryChargingFull'
import ElectricalServicesIcon from '@mui/icons-material/ElectricalServices'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { fmtW } from '../lib/format'

interface Props {
  solarW: number | null | undefined
  loadW: number | null | undefined
  batteryW: number | null | undefined   // positivo = descarregando
  exportW: number | null | undefined
  importW: number | null | undefined
}

/** Fluxo de energia instantâneo: solar → casa, bateria ⇄, rede ⇄. */
export default function EnergyFlowCard({ solarW, loadW, batteryW, exportW, importW }: Props) {
  const charging = (batteryW ?? 0) < 0
  const discharging = (batteryW ?? 0) > 0
  const exporting = (exportW ?? 0) > 0
  const importing = (importW ?? 0) > 0

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>Fluxo de energia</Typography>
        <Stack spacing={1.5}>
          <FlowRow
            icon={<WbSunnyIcon sx={{ color: '#f6b93b' }} />}
            label="Solar"
            value={fmtW(solarW)}
            direction="none"
          />
          <Divider />
          <FlowRow
            icon={<HomeIcon color="primary" />}
            label="Casa"
            value={fmtW(loadW)}
            direction="in"
            hint="consumindo"
          />
          <FlowRow
            icon={<BatteryChargingFullIcon color={discharging ? 'warning' : 'success'} />}
            label="Bateria"
            value={fmtW(batteryW == null ? null : Math.abs(batteryW))}
            direction={charging ? 'in' : discharging ? 'out' : 'none'}
            hint={charging ? 'carregando' : discharging ? 'descarregando' : 'em repouso'}
          />
          <FlowRow
            icon={<ElectricalServicesIcon color={importing ? 'error' : 'success'} />}
            label="Rede"
            value={fmtW(exporting ? exportW : importing ? importW : 0)}
            direction={exporting ? 'in' : importing ? 'out' : 'none'}
            hint={exporting ? 'exportando' : importing ? 'importando' : 'sem troca'}
          />
        </Stack>
      </CardContent>
    </Card>
  )
}

function FlowRow({ icon, label, value, direction, hint }: {
  icon: React.ReactNode
  label: string
  value: string
  direction: 'in' | 'out' | 'none'
  hint?: string
}) {
  return (
    <Stack direction="row" spacing={1.5} alignItems="center">
      {icon}
      <Typography sx={{ width: 72 }}>{label}</Typography>
      {direction === 'in' && <ArrowForwardIcon fontSize="small" color="disabled" />}
      {direction === 'out' && <ArrowBackIcon fontSize="small" color="disabled" />}
      {direction === 'none' && <span style={{ width: 20 }} />}
      <Typography variant="h6" sx={{ flexGrow: 1 }}>{value}</Typography>
      {hint && (
        <Typography variant="caption" color="text.secondary">{hint}</Typography>
      )}
    </Stack>
  )
}
