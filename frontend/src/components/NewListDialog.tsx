import { useState, type FormEvent } from 'react'
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Radio,
  RadioGroup,
  TextField,
} from '@mui/material'
import type { Visibility } from '../api/todoApi'

interface Props {
  open: boolean
  onClose: () => void
  /** Rejects if the list could not be created (the error is already shown to the user). */
  onCreate: (title: string, visibility: Visibility) => Promise<void>
}

export function NewListDialog({ open, onClose, onCreate }: Props) {
  const [title, setTitle] = useState('')
  const [visibility, setVisibility] = useState<Visibility>('PUBLIC')
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (busy) return
    setBusy(true)
    try {
      await onCreate(title.trim(), visibility)
      setTitle('')
      onClose()
    } catch {
      // keep the dialog open with the input, so the user can fix it and retry
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <form onSubmit={submit}>
        <DialogTitle>New list</DialogTitle>
        <DialogContent>
          <TextField
            label="Title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 100 } }}
            fullWidth
            required
            autoFocus
            margin="dense"
          />
          <RadioGroup row value={visibility} onChange={(e) => setVisibility(e.target.value as Visibility)}>
            <FormControlLabel value="PUBLIC" control={<Radio />} label="Public" />
            <FormControlLabel value="PRIVATE" control={<Radio />} label="Private" />
          </RadioGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={busy || !title.trim()}>
            Create
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
