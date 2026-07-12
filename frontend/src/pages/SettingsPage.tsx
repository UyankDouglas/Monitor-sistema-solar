import { useState } from 'react'
import {
  Alert as MuiAlert, Button, Card, CardContent, Dialog, DialogActions,
  DialogContent, DialogTitle, IconButton, MenuItem, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import EditIcon from '@mui/icons-material/Edit'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Configuration } from '../api/types'
import { fmtDateTime } from '../lib/format'

/** Agrupamento e rótulos amigáveis das chaves de configuração. */
const GROUPS: Array<{ title: string; hint?: string; keys: Array<{ key: string; label: string; options?: string[] }> }> = [
  {
    title: 'Coleta de dados',
    hint: 'Mudanças valem no próximo ciclo, sem reiniciar.',
    keys: [
      { key: 'provider.mode', label: 'Origem dos dados', options: ['SIMULATED', 'CLOUD', 'LOCAL'] },
      { key: 'scheduler.reading-interval-ms', label: 'Intervalo de leitura (ms)' },
    ],
  },
  {
    title: 'Economia',
    keys: [
      { key: 'energy.kwh-price', label: 'Tarifa do kWh' },
      { key: 'energy.currency', label: 'Moeda' },
      { key: 'energy.co2-factor-kg-per-kwh', label: 'Fator CO₂ (kg/kWh)' },
      { key: 'app.timezone', label: 'Fuso horário' },
    ],
  },
  {
    title: 'Solarman Cloud (modo CLOUD)',
    hint: 'Credenciais da API oficial — solicite em home.solarmanpv.com → API Service.',
    keys: [
      { key: 'provider.cloud.app-id', label: 'App ID' },
      { key: 'provider.cloud.app-secret', label: 'App Secret' },
      { key: 'provider.cloud.email', label: 'E-mail da conta Solarman' },
      { key: 'provider.cloud.password-sha256', label: 'Senha (SHA-256 hex)' },
    ],
  },
  {
    title: 'Logger local (modo LOCAL)',
    hint: 'Comunicação direta com o stick logger na porta 8899 — sem internet.',
    keys: [
      { key: 'provider.local.logger-ip', label: 'IP do logger' },
      { key: 'provider.local.logger-port', label: 'Porta' },
      { key: 'provider.local.logger-serial', label: 'Serial do logger (numérico)' },
    ],
  },
]

export default function SettingsPage() {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<{ key: string; label: string; options?: string[] } | null>(null)
  const [draft, setDraft] = useState('')
  const [error, setError] = useState<string | null>(null)

  const settings = useQuery<Configuration[]>({
    queryKey: ['settings'],
    queryFn: async () => (await api.get<Configuration[]>('/api/settings')).data,
  })

  const save = useMutation({
    mutationFn: async ({ key, value }: { key: string; value: string }) =>
      (await api.put<Configuration>(`/api/settings/${key}`, { value })).data,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['settings'] })
      setEditing(null)
      setError(null)
    },
    onError: (e: unknown) => {
      const detail = (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail
      setError(detail ?? 'Não foi possível salvar.')
    },
  })

  const byKey = new Map((settings.data ?? []).map(s => [s.key, s]))

  function openEditor(item: { key: string; label: string; options?: string[] }) {
    const current = byKey.get(item.key)
    setDraft(current?.secret ? '' : current?.value ?? '')
    setError(null)
    setEditing(item)
  }

  return (
    <Stack spacing={3}>
      <Typography variant="h5">Configurações</Typography>
      <MuiAlert severity="info">
        Para conectar seu inversor real: preencha as credenciais do grupo
        correspondente (Cloud ou Logger local), informe o serial real do
        inversor e então troque a <b>Origem dos dados</b>. O sistema passa a
        coletar do equipamento no ciclo seguinte.
      </MuiAlert>

      {GROUPS.map(group => (
        <Card key={group.title}>
          <CardContent>
            <Typography variant="h6">{group.title}</Typography>
            {group.hint && (
              <Typography variant="caption" color="text.secondary">{group.hint}</Typography>
            )}
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Configuração</TableCell>
                    <TableCell>Valor</TableCell>
                    <TableCell>Atualizado</TableCell>
                    <TableCell align="right" />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {group.keys.map(item => {
                    const cfg = byKey.get(item.key)
                    return (
                      <TableRow key={item.key} hover>
                        <TableCell sx={{ width: '30%' }}>
                          {item.label}
                          <Typography variant="caption" display="block" color="text.secondary">
                            {item.key}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          {cfg == null ? '—'
                            : cfg.value === '' ? <em>não configurado</em>
                            : cfg.value}
                        </TableCell>
                        <TableCell>{fmtDateTime(cfg?.updatedAt)}</TableCell>
                        <TableCell align="right">
                          <IconButton size="small" onClick={() => openEditor(item)}>
                            <EditIcon fontSize="small" />
                          </IconButton>
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>
      ))}

      <Dialog open={editing !== null} onClose={() => setEditing(null)} fullWidth maxWidth="sm">
        <DialogTitle>{editing?.label}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {error && <MuiAlert severity="error">{error}</MuiAlert>}
            {byKey.get(editing?.key ?? '')?.secret && (
              <MuiAlert severity="warning">
                Valor sensível: o atual nunca é exibido. Para substituí-lo,
                digite o novo valor — salvar em branco está bloqueado.
              </MuiAlert>
            )}
            {editing?.options ? (
              <TextField select fullWidth label="Valor" value={draft}
                onChange={e => setDraft(e.target.value)}>
                {editing.options.map(opt => (
                  <MenuItem key={opt} value={opt}>{opt}</MenuItem>
                ))}
              </TextField>
            ) : (
              <TextField fullWidth label="Valor" value={draft} autoFocus
                onChange={e => setDraft(e.target.value)} />
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>Cancelar</Button>
          <Button
            variant="contained"
            // Segredo + campo vazio bloqueado: abrir "só para conferir" e
            // salvar apagaria a credencial armazenada silenciosamente.
            disabled={save.isPending || editing == null
              || ((byKey.get(editing.key)?.secret ?? false) && draft.trim() === '')}
            onClick={() => editing && save.mutate({ key: editing.key, value: draft })}
          >
            Salvar
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
