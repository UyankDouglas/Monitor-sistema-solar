import ReactECharts from 'echarts-for-react'
import { Box } from '@mui/material'

interface Props {
  categories: string[]
  values: Array<number | null>
  seriesName: string
  unit?: string
  height?: number
  mode: 'light' | 'dark'
  color?: string
}

/** Gráfico de barras reutilizável (geração diária, mensal, anual). */
export default function GenerationBars({
  categories, values, seriesName, unit = 'kWh', height = 300, mode, color = '#f6b93b',
}: Props) {
  const option = {
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: unknown) => (v == null ? '—' : `${Number(v).toLocaleString('pt-BR')} ${unit}`),
    },
    grid: { left: 56, right: 16, top: 24, bottom: 28 },
    xAxis: { type: 'category', data: categories },
    yAxis: { type: 'value', name: unit },
    series: [{
      name: seriesName,
      type: 'bar',
      data: values,
      color,
      barMaxWidth: 28,
      itemStyle: { borderRadius: [4, 4, 0, 0] },
    }],
  }
  return (
    <Box sx={{ height }}>
      <ReactECharts
        option={option}
        notMerge
        style={{ height: '100%', width: '100%' }}
        theme={mode === 'dark' ? 'dark' : undefined}
      />
    </Box>
  )
}
