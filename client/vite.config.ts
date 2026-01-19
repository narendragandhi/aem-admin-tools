import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:10004',
        changeOrigin: true,
      },
    },
  },
  build: {
    target: 'esnext',
  },
});
