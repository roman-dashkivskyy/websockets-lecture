/**
 * sessionStorage is per browser tab, so two windows can be signed in as different users
 * (Alice and Bob side by side in the demo). Storage can be unavailable, so never throw.
 */
export function readSession(key: string): string | null {
  try {
    return sessionStorage.getItem(key)
  } catch {
    return null
  }
}

export function writeSession(key: string, value: string | null): void {
  try {
    if (value === null) sessionStorage.removeItem(key)
    else sessionStorage.setItem(key, value)
  } catch {
    // ignore: the app still works, it just forgets the value on reload
  }
}
