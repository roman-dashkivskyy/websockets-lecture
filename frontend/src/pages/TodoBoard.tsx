import { useCallback, useEffect, useState } from 'react'
import { Alert, Box, Button, Grid, Snackbar, Stack, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import RefreshIcon from '@mui/icons-material/Refresh'
import { useTodoApi, type TodoItem, type TodoList, type Visibility } from '../api/todoApi'
import { NewListDialog } from '../components/NewListDialog'
import { TodoListCard } from '../components/TodoListCard'

/**
 * Phase 1: plain HTTP. Our own changes show up immediately (we update local state from the
 * response), but changes made by other users appear only after "Refresh".
 */
export function TodoBoard() {
  const api = useTodoApi()
  const [lists, setLists] = useState<TodoList[] | null>(null)
  const [loadedAt, setLoadedAt] = useState<Date | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)

  const report = (e: unknown) => setError(e instanceof Error ? e.message : String(e))

  const refresh = useCallback(() => {
    api
      .lists()
      .then((result) => {
        setLists(result)
        setLoadedAt(new Date())
      })
      .catch(report)
  }, [api])

  useEffect(refresh, [refresh])

  /** Replace one list in local state with the result of a change. */
  const updateList = (listId: number, change: (list: TodoList) => TodoList | null) =>
    setLists((current) =>
      (current ?? []).flatMap((list) => {
        if (list.id !== listId) return [list]
        const changed = change(list)
        return changed ? [changed] : []
      }),
    )

  /** Show the error, then fail the promise so forms keep the user's input. */
  const reportAndRethrow = (e: unknown): never => {
    report(e)
    throw e
  }

  const createList = (title: string, visibility: Visibility) =>
    api
      .createList(title, visibility)
      .then((created) => setLists((current) => [...(current ?? []), created]))
      .catch(reportAndRethrow)

  const run = (action: () => Promise<void>) => () => action().catch(report)

  return (
    <Box>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 3, flexWrap: 'wrap' }}>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
          New list
        </Button>
        <Button variant="outlined" startIcon={<RefreshIcon />} onClick={refresh}>
          Refresh
        </Button>
        <Typography variant="body2" color="text.secondary">
          {loadedAt ? `Loaded at ${loadedAt.toLocaleTimeString()}` : 'Loading…'}
        </Typography>
      </Stack>

      {lists && lists.length === 0 && <Alert severity="info">No lists yet. Create the first one.</Alert>}

      <Grid container spacing={2}>
        {lists?.map((list) => (
          <Grid key={list.id} size={{ xs: 12, sm: 6, md: 4 }}>
            <TodoListCard
              list={list}
              onRename={(title) =>
                api
                  .renameList(list.id, title)
                  .then((renamed) => updateList(list.id, () => renamed))
                  .catch(reportAndRethrow)
              }
              onDelete={run(async () => {
                await api.deleteList(list.id)
                updateList(list.id, () => null)
              })}
              onAddItem={(title) =>
                api
                  .addItem(list.id, title)
                  .then((item) => updateList(list.id, (l) => ({ ...l, items: [...l.items, item] })))
                  .catch(reportAndRethrow)
              }
              onToggleItem={(item: TodoItem) =>
                api
                  .updateItem(item.id, { done: !item.done })
                  .then((updated) =>
                    updateList(list.id, (l) => ({
                      ...l,
                      items: l.items.map((i) => (i.id === updated.id ? updated : i)),
                    })),
                  )
                  .catch(report)
              }
              onDeleteItem={(item: TodoItem) =>
                api
                  .deleteItem(item.id)
                  .then(() => updateList(list.id, (l) => ({ ...l, items: l.items.filter((i) => i.id !== item.id) })))
                  .catch(report)
              }
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
