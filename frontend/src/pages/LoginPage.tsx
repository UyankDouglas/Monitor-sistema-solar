import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Alert, Box, Button, Card, CardContent, Stack, TextField, Typography,
} from '@mui/material'
import BoltIcon from '@mui/icons-material/Bolt'
import { useAuth } from '../auth/AuthContext'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const user = await login(username, password)
      navigate(user.mustChangePassword ? '/change-password' : '/', { replace: true })
    } catch (e: unknown) {
      const detail = (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail
      setError(detail ?? 'Não foi possível entrar. Verifique usuário e senha.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', px: 2 }}>
      <Card sx={{ width: '100%', maxWidth: 400 }}>
        <CardContent sx={{ p: 4 }}>
          <Stack component="form" onSubmit={handleSubmit} spacing={3}>
            <Stack direction="row" spacing={1} alignItems="center" justifyContent="center">
              <BoltIcon color="warning" fontSize="large" />
              <Typography variant="h5">Monitor Solar Deye</Typography>
            </Stack>

            {error && <Alert severity="error">{error}</Alert>}

            <TextField
              label="Usuário"
              value={username}
              onChange={e => setUsername(e.target.value)}
              autoComplete="username"
              autoFocus
              required
            />
            <TextField
              label="Senha"
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
            <Button type="submit" variant="contained" size="large" disabled={submitting}>
              {submitting ? 'Entrando…' : 'Entrar'}
            </Button>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  )
}
