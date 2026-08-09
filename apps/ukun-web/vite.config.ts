/// <reference types="vitest" />
import { defineConfig, loadEnv, UserConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  // En producción el SPA lo sirve el gateway y todo es mismo origen (ADR-0003). En desarrollo el
  // dev server hace de ese mismo origen: `VITE_API_URL` se queda vacía y es el proxy quien lleva
  // `/api` al servicio real, así que ningún hook sabe contra qué está hablando. El destino es
  // configurable porque un worktree desplaza los puertos (docs/06-entorno-local-tilt.md).
  const apiTarget = env.API_PROXY_TARGET || `http://localhost:${env.API_PORT || 8081}`;

  return {
    plugins: [react()],
    css: {
      preprocessorOptions: {
        scss: {
          api: 'modern-compiler',
          silenceDeprecations: ['import', 'global-builtin', 'if-function', 'color-functions'],
        },
      },
    },
    server: {
      port: 5173,
      host: true, // Needed for docker/tilt
      proxy: {
        '/api': { target: apiTarget, changeOrigin: true },
        // Las fotos de producto llegan como URL servible (`/media/**`), compuesta por el servidor.
        '/media': { target: apiTarget, changeOrigin: true },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
    },
  } as UserConfig & { test: any };
});
