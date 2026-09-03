import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Cổng phải khớp identity.frontend-base-url default (:3000) để redirect sau Google về đúng
    port: 3000,
    proxy: {
      // API calls same-origin qua Vite (không lo CORS). Riêng nút Google Login
      // dùng URL tuyệt đối tới gateway để state cookie set đúng origin callback.
      '/api': 'http://localhost:8080',
    },
  },
})
