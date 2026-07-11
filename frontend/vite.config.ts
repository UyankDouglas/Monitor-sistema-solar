import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Proxy de desenvolvimento: encaminha /api e /ws para o backend Spring Boot.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'http://localhost:8080', ws: true },
    },
  },
})
