import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// Proxy de desenvolvimento: encaminha /api e /ws para o backend Spring Boot.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // API e WS nunca passam pelo cache do service worker: dados de energia
      // são tempo real — servir resposta velha seria pior que falhar.
      workbox: {
        navigateFallbackDenylist: [/^\/api\//, /^\/ws/],
        runtimeCaching: [],
      },
      includeAssets: ['icon.svg'],
      manifest: {
        name: 'Monitor Solar Deye',
        short_name: 'Monitor Solar',
        description: 'Monitoramento de geração de energia solar — inversor Deye',
        lang: 'pt-BR',
        theme_color: '#1a2332',
        background_color: '#1a2332',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: '/icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' },
          { src: '/icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'maskable' },
        ],
      },
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'http://localhost:8080', ws: true },
    },
  },
})
