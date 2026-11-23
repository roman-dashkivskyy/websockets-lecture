import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Box, Button, Chip, Grid, Snackbar, Stack } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import { useApi } from '../api/ApiContext'
import { useTodoApi, type TodoItem, type TodoList, type Visibility } from '../api/todoApi'
import { useAuth } from '../auth/AuthContext'
import { NewListDialog } from '../components/NewListDialog'
import { TodoListCard } from '../components/TodoListCard'
import { applyEvent, type TodoEvent } from '../realtime/events'
import { useTodoSocket, type SocketStatus } from '../realtime/useTodoSocket'

/**
 * HTTP for commands, WebSocket for updates. The lists are (re)loaded on every new connection, because
 * updates may have been missed while disconnected; after that every change arrives as a push message.
 * Phase 3: a change is pushed only by the instance that handled it, so with several instances behind
 * the load balancer, browsers whose socket lives on another instance miss it.
 */
export function TodoBoard() {
  const api = useTodoApi()
  const { target } = useApi()
  const { session } = useAuth()
  const [lists, setLists] = useState<TodoList[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)

  // Messages that arrive while GET /lists is still running are applied right after it.
  const loaded = useRef(false)
  const pending = useRef<TodoEvent[]>([])

  const onEvent = useCallback((event: TodoEvent) => {
    if (!loaded.current) {
      pending.current.push(event)
      return
    }
    setLists((current) => applyEvent(current ?? [], event))
  }, [])

  const status = useTodoSocket(target.basePath, session!.token, onEvent)

  const report = (e: unknown) => setError(e instanceof Error ? e.message : String(e))

  const loadKey = loadKeyFor(status)

  useEffect(() => {
    if (loadKey === null) return
    // While reconnecting we reload quietly: errors (e.g. the instance is down) would only spam the screen,
    // but an expired token still gets 401 -> back to sign-in (handled inside useTodoApi).
    const quiet = loadKey.startsWith('retry-')
    let cancelled = false // a newer connection or retry started a newer load
    loaded.current = false
    pending.current = []
    api
      .lists()
      .then((result) => {
        if (cancelled) return
        loaded.current = true
        setLists(pending.current.reduce(applyEvent, result))
        pending.current = []
      })
      .catch((e) => {
        if (cancelled) return
        pending.current = []
        if (!quiet) report(e)
      })
    return () => {
      cancelled = true
    }
  }, [loadKey, api])

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

/**
 * When to (re)load the lists:
 * - after every successful (re)connect: we may have missed updates while disconnected;
 * - after every failed reconnect attempt (at the backoff pace): keeps the board fresh during an outage,
 *   and a dead token reaches the normal 401 -> sign-in path instead of retrying forever.
 */
function loadKeyFor(status: SocketStatus): string | null {
  if (status.state === 'live') return `connection-${status.connection}`
  if (status.state === 'reconnecting') return `retry-${status.attempt}`
  return null
}

function StatusChip({ status }: { status: SocketStatus }) {
  switch (status.state) {
    case 'live':
      return <Chip color="success" label={status.instance ? `WebSocket: live on ${status.instance}` : 'WebSocket: live'} />
    case 'connecting':
      return <Chip label="WebSocket: connecting…" />
    case 'reconnecting':
      return <Chip color="warning" label={`WebSocket: reconnecting in ~${status.inSeconds} s`} />
  }
}
