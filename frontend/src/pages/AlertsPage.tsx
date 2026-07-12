import { useEffect, useState } from 'react'
import {
  Button, Card, CardContent, Chip, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TablePagination, TableRow, ToggleButton,
  ToggleButtonGroup, Typography,
} from '@mui/material'
import CheckIcon from '@mui/icons-material/Check'
import DoneAllIcon from '@mui/icons-material/DoneAll'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Alert, Page } from '../api/types'
import { fmtDateTime } from '../lib/format'

const TYPE_LABELS: Record<string, string> = {
  INVERTER_OFFLINE: 'Inversor offline',
  NO_GENERATION_DAYTIME: 'Sem geração no horário solar',
  HIGH_TEMPERATURE: 'Temperatura alta',
  LOW_BATTERY: 'Bateria baixa',
  INVERTER_FAULT: 'Falha do inversor',
  COMMUNICATION_LOSS: 'Perda de comunicação',
}

const SEVERITY_COLOR = { INFO: 'info', WARNING: 'warning', CRITICAL: 'error' } as const
const STATUS_LABEL = { ACTIVE: 'Ativo', ACKNOWLEDGED: 'Reconhecido', RESOLVED: 'Resolvido' } as const

export default function AlertsPage() {
  const queryClient = useQueryClient()
  const [statusFilter, setStatusFilter] = useState<string | null>('ACTIVE')
  const [page, setPage] = useState(0)
  const size = 20

  const alerts = useQuery<Page<Alert>>({
    queryKey: ['alerts', statusFilter, page],
    queryFn: async () => (await api.get<Page<Alert>>('/api/alerts', {
      params: { page, size, ...(statusFilter ? { status: statusFilter } : {}) },
    })).data,
    refetchInterval: 30_000,
  })

  // Se a página atual deixou de existir (resolvi o último alerta da página
  // 2, ou refetch em background encolheu a lista), volta à última válida —
  // sem isso a tabela mostraria "Nenhum alerta" com alertas existentes.
  const totalPages = alerts.data ? Math.ceil(alerts.data.totalElements / size) : undefined
  useEffect(() => {
    if (totalPages !== undefined && page > 0 && page > totalPages - 1) {
      setPage(Math.max(0, totalPages - 1))
    }
  }, [totalPages, page])

  const act = useMutation({
    mutationFn: async ({ id, action }: { id: number; action: 'acknowledge' | 'resolve' }) =>
      api.post(`/api/alerts/${id}/${action}`),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: ['alerts'] }),
  })

  const rows = alerts.data?.content ?? []

  return (
    <Stack spacing={3}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Alertas</Typography>
        <ToggleButtonGroup
          size="small"
          exclusive
          value={statusFilter}
          onChange={(_, value: string | null) => { setStatusFilter(value); setPage(0) }}
        >
          <ToggleButton value="ACTIVE">Ativos</ToggleButton>
          <ToggleButton value="ACKNOWLEDGED">Reconhecidos</ToggleButton>
          <ToggleButton value="RESOLVED">Resolvidos</ToggleButton>
        </ToggleButtonGroup>
      </Stack>

      <Card>
        <CardContent>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Quando</TableCell>
                  <TableCell>Tipo</TableCell>
                  <TableCell>Severidade</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Mensagem</TableCell>
                  <TableCell align="right">Ações</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6}>
                      <Typography color="text.secondary">
                        {alerts.isLoading ? 'Carregando…' : 'Nenhum alerta — tudo em ordem. ✅'}
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
                {rows.map(alert => (
                  <TableRow key={alert.id} hover>
                    <TableCell>{fmtDateTime(alert.triggeredAt)}</TableCell>
                    <TableCell>{TYPE_LABELS[alert.type] ?? alert.type}</TableCell>
                    <TableCell>
                      <Chip size="small" label={alert.severity}
                        color={SEVERITY_COLOR[alert.severity]} />
                    </TableCell>
                    <TableCell>{STATUS_LABEL[alert.status]}</TableCell>
                    <TableCell>{alert.message}</TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        {alert.status === 'ACTIVE' && (
                          <Button size="small" startIcon={<CheckIcon />}
                            disabled={act.isPending}
                            onClick={() => act.mutate({ id: alert.id, action: 'acknowledge' })}>
                            Reconhecer
                          </Button>
                        )}
                        {alert.status !== 'RESOLVED' && (
                          <Button size="small" startIcon={<DoneAllIcon />} color="success"
                            disabled={act.isPending}
                            onClick={() => act.mutate({ id: alert.id, action: 'resolve' })}>
                            Resolver
                          </Button>
                        )}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={alerts.data?.totalElements ?? 0}
            page={page}
            onPageChange={(_, p) => setPage(p)}
            rowsPerPage={size}
            rowsPerPageOptions={[size]}
            labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`}
          />
        </CardContent>
      </Card>
    </Stack>
  )
}
