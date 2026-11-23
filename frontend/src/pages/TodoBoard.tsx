import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Box, Button, Chip, Grid, Snackbar, Stack } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import { useTodoApi, type TodoItem, type TodoList, type Visibility } from '../api/todoApi'
import { useAuth } from '../auth/AuthContext'
import { NewListDialog } from '../components/NewListDialog'
import { TodoListCard } from '../components/TodoListCard'
import { applyEvent, type TodoEvent } from '../realtime/events'
import { useTodoSocket, type SocketStatus } from '../realtime/useTodoSocket'

/**
 * Phase 2: HTTP for commands, WebSocket for updates.
 * We load the lists once; after that every change, ours or anybody else's, arrives as a push message.
 */
export function TodoBoard() {
  const api = useTodoApi()
  const { session } = useAuth()
  const [lists, setLists] = useState<TodoList[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)

  // Messages that arrive while the first GET /lists is still running are applied right after it.
  const loaded = useRef(false)
  const pending = useRef<TodoEvent[]>([])

  const onEvent = useCallback((event: TodoEvent) => {
    if (!loaded.current) {
      pending.current.push(event)
      return
    }
    setLists((current) => applyEvent(current ?? [], event))
  }, [])

  const status = useTodoSocket(session!.token, onEvent)

  const report = (e: unknown) => setError(e instanceof Error ? e.message : String(e))

  // Load once the socket is open, so no change can slip in between the load and the first push.
  // If the handshake was refused, load anyway: a dead token then gets the normal 401 -> back to sign-in.
  useEffect(() => {
    if (status === 'connecting' || loaded.current) return
    api
      .lists()
      .then((result) => {
        loaded.current = true
        setLists(pending.current.reduce(applyEvent, result))
        pending.current = []
      })
      .catch((e) => {
        pending.current = []
        report(e)
      })
  }, [status, api])

  /** Show the error, then fail the promise so forms keep the user's input. */
  const reportAndRethrow = (e: unknown): never => {
    report(e)
    throw e
  }

  // Commands only: the new state comes back over the WebSocket.
  const createList = (title: string, visibility: Visibility) =>
    api.createList(title, visibility).then(() => undefined, reportAndRethrow)

  return (
    <Box>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 3, flexWrap: 'wrap' }}>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
          New list
        </Button>
        <StatusChip status={status} />
      </Stack>

      {lists && lists.length === 0 && <Alert severity="info">No lists yet. Create the first one.</Alert>}

      <Grid container spacing={2}>
        {lists?.map((list) => (
          <Grid key={list.id} size={{ xs: 12, sm: 6, md: 4 }}>
            <TodoListCard
              list={list}
              mine={list.owner === session!.username}
              onRename={(title) => api.renameList(list.id, title).then(() => undefined, reportAndRethrow)}
              onDelete={() => api.deleteList(list.id).catch(report)}
              onAddItem={(title) => api.addItem(list.id, title).then(() => undefined, reportAndRethrow)}
              onToggleItem={(item: TodoItem) =>
                api.updateItem(item.id, { done: !item.done }).then(() => undefined, report)
              }
              onDeleteItem={(item: TodoItem) => api.deleteItem(item.id).catch(report)}
            />
          </Grid>
        ))}
      </Grid>

      <NewListDialog open={dialogOpen} onClose={() => setDialogOpen(false)} onCreate={createList} />

      <Snackbar open={error !== null} autoHideDuration={5000} onClose={() => setError(null)}>
        <Alert severity="error" onClose={() => setError(null)} variant="filled">
          {error}
        </Alert>
      </Snackbar>
    </Box>
  )
}

function StatusChip({ status }: { status: SocketStatus }) {
  switch (status) {
    case 'live':
      return <Chip color="success" label="Live" />
    case 'connecting':
      return <Chip label="Connecting…" />
    case 'closed':
      return <Chip color="error" label="Disconnected: reload the page" />
  }
}
