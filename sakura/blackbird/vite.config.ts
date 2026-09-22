import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev port 5173, northstar is on 3000 and soundwave on 4200.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
});
