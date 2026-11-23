/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** How many backend instances the UI offers in its "Backend" selector (default 3). */
  readonly VITE_BACKEND_COUNT?: string
}
