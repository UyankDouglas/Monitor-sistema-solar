import type { ReactNode } from 'react'
import { Card, CardContent, Stack, Typography } from '@mui/material'

interface Props {
  icon: ReactNode
  label: string
  value: string
  hint?: string
  color?: string
}

/** Card compacto de indicador para o grid do dashboard. */
export default function StatCard({ icon, label, value, hint, color }: Props) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Stack sx={{ color: color ?? 'primary.main' }}>{icon}</Stack>
          <Stack sx={{ minWidth: 0 }}>
            <Typography variant="caption" color="text.secondary" noWrap>
              {label}
            </Typography>
            <Typography variant="h6" sx={{ lineHeight: 1.2 }} noWrap>
              {value}
            </Typography>
            {hint && (
              <Typography variant="caption" color="text.secondary" noWrap>
                {hint}
              </Typography>
            )}
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  )
}
