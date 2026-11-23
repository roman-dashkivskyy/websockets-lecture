import { AppBar, Box, Button, Toolbar, Typography } from '@mui/material'
import LogoutIcon from '@mui/icons-material/Logout'
import { useAuth } from '../auth/AuthContext'

export function AppHeader() {
  const { session, logout } = useAuth()

  return (
    <AppBar position="static" elevation={0}>
      <Toolbar sx={{ gap: 2 }}>
        <Typography variant="h6" component="h1" sx={{ flexGrow: 1 }}>
          WebSockets Lecture
        </Typography>

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
