import { Box, Container } from '@mui/material'
import { useApi } from './api/ApiContext'
import { useAuth } from './auth/AuthContext'
import { AppHeader } from './components/AppHeader'
import { LoginPage } from './pages/LoginPage'
import { TodoBoard } from './pages/TodoBoard'

export default function App() {
  const { session } = useAuth()
  const { target } = useApi()

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppHeader />
      <Container maxWidth="lg" sx={{ py: 3 }}>
        {/* key: another user or another backend gets a fresh board (and a fresh socket) */}
        {session ? <TodoBoard key={`${session.username}@${target.id}`} /> : <LoginPage />}
      </Container>
    </Box>
  )
}
