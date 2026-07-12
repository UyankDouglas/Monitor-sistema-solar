import { useState } from 'react'
import {
  Alert as MuiAlert, Button, Card, CardContent, Checkbox, Chip, Dialog,
  DialogActions, DialogContent, DialogTitle, FormControlLabel, Stack, Switch,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField,
  Typography,
} from '@mui/material'
import PersonAddIcon from '@mui/icons-material/PersonAdd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { UserInfo } from '../auth/types'
import { useAuth } from '../auth/AuthContext'

export default function UsersPage() {
  const { user: me } = useAuth()
  const queryClient = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({
    username: '', email: '', fullName: '', temporaryPassword: '', admin: false,
  })

  const users = useQuery<UserInfo[]>({
    queryKey: ['users'],
    queryFn: async () => (await api.get<UserInfo[]>('/api/users')).data,
  })

  const create = useMutation({
    mutationFn: async () => (await api.post<UserInfo>('/api/users', {
      username: form.username,
      email: form.email,
      fullName: form.fullName,
      temporaryPassword: form.temporaryPassword,
      roles: form.admin ? ['ADMIN', 'USER'] : ['USER'],
    })).data,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['users'] })
      setCreateOpen(false)
      setForm({ username: '', email: '', fullName: '', temporaryPassword: '', admin: false })
      setError(null)
    },
    onError: (e: unknown) => {
      const data = (e as { response?: { data?: { detail?: string; fields?: Record<string, string> } } })
        ?.response?.data
      const fieldError = data?.fields ? Object.values(data.fields)[0] : undefined
      setError(data?.detail ?? fieldError ?? 'Não foi possível criar o usuário.')
    },
  })

  const toggle = useMutation({
    mutationFn: async (id: number) => api.post(`/api/users/${id}/toggle-enabled`),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: ['users'] }),
  })

  return (
    <Stack spacing={3}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Usuários</Typography>
        <Button variant="contained" startIcon={<PersonAddIcon />} onClick={() => setCreateOpen(true)}>
          Novo usuário
        </Button>
      </Stack>

      <Card>
        <CardContent>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Usuário</TableCell>
                  <TableCell>Nome</TableCell>
                  <TableCell>E-mail</TableCell>
                  <TableCell>Papéis</TableCell>
                  <TableCell>Situação</TableCell>
                  <TableCell align="right">Ativo</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(users.data ?? []).map(u => (
                  <TableRow key={u.id} hover>
                    <TableCell>{u.username}</TableCell>
                    <TableCell>{u.fullName}</TableCell>
                    <TableCell>{u.email}</TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={0.5}>
                        {u.roles.map(r => (
                          <Chip key={r} label={r} size="small"
                            color={r === 'ADMIN' ? 'secondary' : 'default'} />
                        ))}
                      </Stack>
                    </TableCell>
                    <TableCell>
                      {u.mustChangePassword
                        ? <Chip label="troca de senha pendente" size="small" color="warning" />
                        : <Chip label="ok" size="small" color="success" variant="outlined" />}
                    </TableCell>
                    <TableCell align="right">
                      <Switch
                        checked={u.enabled}
                        disabled={u.username === me?.username || toggle.isPending}
                        onChange={() => toggle.mutate(u.id)}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Novo usuário</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {error && <MuiAlert severity="error">{error}</MuiAlert>}
            <TextField label="Username" value={form.username} autoFocus
              helperText="letras, números, ponto, hífen e underscore"
              onChange={e => setForm(f => ({ ...f, username: e.target.value }))} />
            <TextField label="E-mail" type="email" value={form.email}
              onChange={e => setForm(f => ({ ...f, email: e.target.value }))} />
            <TextField label="Nome completo" value={form.fullName}
              onChange={e => setForm(f => ({ ...f, fullName: e.target.value }))} />
            <TextField label="Senha temporária" type="password" value={form.temporaryPassword}
              helperText="mínimo 8 caracteres; o usuário trocará no primeiro acesso"
              onChange={e => setForm(f => ({ ...f, temporaryPassword: e.target.value }))} />
            <FormControlLabel
              control={<Checkbox checked={form.admin}
                onChange={e => setForm(f => ({ ...f, admin: e.target.checked }))} />}
              label="Administrador (gerencia configurações e usuários)"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancelar</Button>
          <Button variant="contained" disabled={create.isPending} onClick={() => create.mutate()}>
            Criar
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
