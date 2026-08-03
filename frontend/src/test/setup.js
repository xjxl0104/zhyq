import { vi } from 'vitest'

globalThis.ResizeObserver = class {
  observe() {}
  unobserve() {}
  disconnect() {}
}

globalThis.matchMedia = globalThis.matchMedia || (() => ({
  matches: false,
  addEventListener: vi.fn(),
  removeEventListener: vi.fn()
}))
