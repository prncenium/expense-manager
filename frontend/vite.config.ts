import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Dev: /api is proxied to the Spring Boot backend, so by default the app calls relative /api URLs.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // The repo keeps a single sample CSV in ../sample-data (the backend bundles the same file in its jar).
  // Using it as the static dir serves it at /expenses.csv in dev and copies it into dist/ on build.
  publicDir: '../sample-data',
  build: {
    rollupOptions: {
      output: {
        // Keep the chart library in its own cacheable chunk.
        manualChunks: { recharts: ['recharts'], react: ['react', 'react-dom', 'react-router-dom'] },
      },
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
