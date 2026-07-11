import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// For GitHub project pages set VITE_BASE=/RepoName/
const base = process.env.VITE_BASE || '/'

export default defineConfig({
  base,
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:4000',
        changeOrigin: true,
      },
    },
  },
})
