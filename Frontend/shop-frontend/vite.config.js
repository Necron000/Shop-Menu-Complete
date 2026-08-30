import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Bind every interface so a tunnel (or another device on the LAN) can reach
    // the dev server, not just loopback.
    host: true,
    // Vite 8 rejects requests whose Host header it does not recognise, which is
    // every *.trycloudflare.com hostname a quick tunnel hands out.
    allowedHosts: ['.trycloudflare.com'],
    // Serving the API from the same origin as the app keeps the browser from
    // ever making a cross-origin request, so one tunnelled hostname is enough
    // and CorsConfig stays out of the picture.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Browsers attach Origin to same-origin POSTs too (not GETs). Forwarded
        // verbatim, it makes Spring classify every proxied write as cross-origin
        // and reject it with 403 "Invalid CORS request" unless the host happens
        // to be one of the two in CorsConfig — so 127.0.0.1, a LAN IP, another
        // port or a tunnel all broke register/login while browsing items worked.
        // Dropping it makes the request look same-origin, which through this
        // proxy it effectively is.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
        },
      },
    },
  },
})
