import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Alert, Box, Button, Card, CardContent, Stack, TextField, Typography,
} from '@mui/material'
import LockResetIcon from '@mui/icons-material/LockReset'
import { useAuth } from '../auth/AuthContext'

/**
 * Troca de senha — obrigatória no primeiro acesso (admin/admin123 de
 * fábrica) e acessível depois para trocas voluntárias.
 */
export default function ChangePasswordPage() {
  const { user, changePassword } = useAuth()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const forced = user?.mustChangePassword === true

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (next !== confirm) {
      setError('A confirmação não confere com a nova senha.')
      return
    }
    if (next.length < 8) {
      setError('A nova senha precisa de pelo menos 8 caracteres.')
      return
    }
    setSubmitting(true)
    try {
      await changePassword(current, next)
      navigate('/', { replace: true })
    } catch (e: unknown) {
      const detail = (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail
      setError(detail ?? 'Não foi possível trocar a senha.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', px: 2 }}>
      <Card sx={{ width: '100%', maxWidth: 440 }}>
        <CardContent sx={{ p: 4 }}>
          <Stack component="form" onSubmit={handleSubmit} spacing={3}>
            <Stack direction="row" spacing={1} alignItems="center">
              <LockResetIcon color="primary" fontSize="large" />
              <Typography variant="h5">Trocar senha</Typography>
            </Stack>

            {forced && (
              <Alert severity="warning">
                Você está usando a senha inicial de fábrica. Por segurança,
                defina uma nova senha antes de continuar.
              </Alert>
            )}
            {error && <Alert severity="error">{error}</Alert>}

            <TextField
              label="Senha atual"
              type="password"
              value={current}
              onChange={e => setCurrent(e.target.value)}
              autoComplete="current-password"
              autoFocus
              required
            />
            <TextField
              label="Nova senha"
              type="password"
              value={next}
              onChange={e => setNext(e.target.value)}
              autoComplete="new-password"
              helperText="Mínimo de 8 caracteres"
              required
            />
            <TextField
              label="Confirmar nova senha"
              type="password"
              value={confirm}
              onChange={e => setConfirm(e.target.value)}
              autoComplete="new-password"
              required
            />
            <Button type="submit" variant="contained" size="large" disabled={submitting}>
              {submitting ? 'Salvando…' : 'Salvar nova senha'}
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  )
}
