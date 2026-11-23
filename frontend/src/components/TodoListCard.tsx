import { useRef, useState, type FormEvent } from 'react'
import {
  Card,
  CardContent,
  CardHeader,
  Checkbox,
  Chip,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  TextField,
  Tooltip,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/DeleteOutlined'
import EditIcon from '@mui/icons-material/EditOutlined'
import type { TodoItem, TodoList } from '../api/todoApi'

interface Props {
  list: TodoList
  /** the signed-in user owns this list, so may rename or delete it */
  mine: boolean
  onRename: (title: string) => Promise<void>
  onDelete: () => Promise<void>
  onAddItem: (title: string) => Promise<void>
  onToggleItem: (item: TodoItem) => Promise<void>
  onDeleteItem: (item: TodoItem) => Promise<void>
}

export function TodoListCard({ list, mine, onRename, onDelete, onAddItem, onToggleItem, onDeleteItem }: Props) {
  const [newItem, setNewItem] = useState('')
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState(list.title)
  // Guards against double requests (double Enter, or Enter followed by blur).
  const adding = useRef(false)
  const renaming = useRef(false)

  const addItem = async (e: FormEvent) => {
    e.preventDefault()
    const text = newItem.trim()
    if (!text || adding.current) return
    adding.current = true
    try {
      await onAddItem(text)
      setNewItem('')
    } catch {
      // keep the text so the user can retry; the error is already shown
    } finally {
      adding.current = false
    }
  }

  const rename = async (e: FormEvent) => {
    e.preventDefault()
    if (renaming.current) return
    renaming.current = true
    setEditing(false)
    const text = title.trim()
    try {
      if (text && text !== list.title) await onRename(text)
    } catch {
      // the error is already shown; the old title stays
    } finally {
      renaming.current = false
    }
  }

  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardHeader
        title={
          editing ? (
            <form onSubmit={rename}>
              <TextField
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                onBlur={rename}
                size="small"
                slotProps={{ htmlInput: { maxLength: 100 } }}
                autoFocus
                fullWidth
              />
            </form>
          ) : (
            list.title
          )
        }
        subheader={
          <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
            <Chip
              size="small"
              label={list.visibility === 'PUBLIC' ? 'Public' : 'Private'}
              color={list.visibility === 'PUBLIC' ? 'primary' : 'default'}
            />
            {!mine && <Chip size="small" variant="outlined" label={`by ${list.owner}`} />}
          </Stack>
        }
        action={
          mine && (
            <>
              <Tooltip title="Rename">
                <IconButton
                  aria-label="Rename list"
                  onClick={() => {
                    setTitle(list.title)
                    setEditing(true)
                  }}
                >
                  <EditIcon />
                </IconButton>
              </Tooltip>
              <Tooltip title="Delete list">
                <IconButton aria-label="Delete list" onClick={onDelete}>
                  <DeleteIcon />
                </IconButton>
              </Tooltip>
            </>
          )
        }
      />
      <CardContent sx={{ pt: 0 }}>
        <List dense disablePadding>
          {list.items.map((item) => (
            <ListItem
              key={item.id}
              disablePadding
              secondaryAction={
                <IconButton edge="end" aria-label={`Delete ${item.title}`} onClick={() => onDeleteItem(item)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              }
            >
              <ListItemButton dense onClick={() => onToggleItem(item)}>
                <ListItemIcon sx={{ minWidth: 36 }}>
                  <Checkbox edge="start" checked={item.done} tabIndex={-1} disableRipple size="small" />
                </ListItemIcon>
                <ListItemText
                  primary={item.title}
                  sx={{
                    textDecoration: item.done ? 'line-through' : 'none',
                    color: item.done ? 'text.secondary' : 'text.primary',
                  }}
                />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
        <form onSubmit={addItem}>
          <TextField
            placeholder="Add an item and press Enter"
            value={newItem}
            onChange={(e) => setNewItem(e.target.value)}
            size="small"
            fullWidth
            slotProps={{ htmlInput: { maxLength: 200 } }}
            sx={{ mt: 1 }}
          />
        </form>
      </CardContent>
    </Card>
  )
}
