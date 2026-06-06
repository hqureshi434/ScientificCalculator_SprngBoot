import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The browser only ever talks to the Vite dev server's own origin. Any request whose path
// starts with /api is transparently forwarded to the Spring backend on :8080, so the browser
// never makes a cross-origin request and CORS never applies. This is why no backend CORS/security
// change is needed — same-origin is achieved here, in the dev proxy.
// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
