import { isDemoEntryEnabled } from './demo-entry.mjs'

export const demoEntryEnabled = isDemoEntryEnabled(import.meta.env.VITE_ENABLE_DEMO_ENTRY)
