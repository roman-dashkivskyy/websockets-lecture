import { useState, type FormEvent } from 'react'
import { Alert, Button, Card, CardContent, Chip, Stack, TextField, Typography } from '@mui/material'
import { useAuth } from '../auth/AuthContext'

/** Seeded in V2__seed.sql. Password for all of them: demo1234 (demo only). */
const DEMO_USERS = ['alice', 'bob', 'carol']

export function LoginPage() {
  const { login, notice } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username.trim(), password)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setBusy(false)
    }
  }

  return (
    <Card variant="outlined" sx={{ maxWidth: 400, mx: 'auto', mt: 6 }}>
      <CardContent>
        <Stack component="form" spacing={2} onSubmit={submit}>
          <Typography variant="h5" component="h2">
            Sign in
          </Typography>

          {notice && <Alert severity="info">{notice}</Alert>}

          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              Demo users:
            </Typography>
            {DEMO_USERS.map((user) => (
              <Chip key={user} label={user} size="small" onClick={() => setUsername(user)} />
            ))}
          </Stack>

          <TextField
            label="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
            autoFocus
          />
          <TextField
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            helperText="Demo password: demo1234"
            required
          />

          {error && <Alert severity="error">{error}</Alert>}

          <Button type="submit" variant="contained" disabled={busy}>
            Sign in
          </Button>
        </Stack>
      </CardContent>
    </Card>
  )
}
