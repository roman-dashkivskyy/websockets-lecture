import { AppBar, Box, Button, Chip, MenuItem, TextField, Toolbar, Tooltip, Typography } from '@mui/material'
import DnsIcon from '@mui/icons-material/Dns'
import LogoutIcon from '@mui/icons-material/Logout'
import { useApi } from '../api/ApiContext'
import { useAuth } from '../auth/AuthContext'
import { backends } from '../config/backends'

export function AppHeader() {
  const { target, setTarget, answeredBy } = useApi()
  const { session, logout } = useAuth()

  return (
    <AppBar position="static" elevation={0}>
      <Toolbar sx={{ gap: 2, flexWrap: 'wrap', py: 1 }}>
        <Typography variant="h6" component="h1" sx={{ flexGrow: 1 }}>
          WebSockets Lecture
        </Typography>

        <TextField
          select
          size="small"
          label="Backend"
          value={target.id}
          onChange={(e) => setTarget(e.target.value)}
          sx={{ minWidth: 160, bgcolor: 'background.paper', borderRadius: 1 }}
        >
          {backends.map((b) => (
            <MenuItem key={b.id} value={b.id}>
              {b.label}
            </MenuItem>
          ))}
        </TextField>

        <Tooltip title="Backend instance that answered the last HTTP request">
          <Chip icon={<DnsIcon />} label={`HTTP answered by: ${answeredBy ?? '—'}`} color="secondary" />
        </Tooltip>

        {session && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="body2">{session.displayName}</Typography>
            <Button color="inherit" size="small" startIcon={<LogoutIcon />} onClick={() => logout()}>
              Sign out
            </Button>
          </Box>
        )}
      </Toolbar>
    </AppBar>
  )
}
