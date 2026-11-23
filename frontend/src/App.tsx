import { Box, Container } from '@mui/material'
import { useAuth } from './auth/AuthContext'
import { AppHeader } from './components/AppHeader'
import { LoginPage } from './pages/LoginPage'
import { TodoBoard } from './pages/TodoBoard'

export default function App() {
  const { session } = useAuth()

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppHeader />
      <Container maxWidth="lg" sx={{ py: 3 }}>
        {/* key: a different user gets a fresh board */}
        {session ? <TodoBoard key={session.username} /> : <LoginPage />}
      </Container>
    </Box>
  )
}
