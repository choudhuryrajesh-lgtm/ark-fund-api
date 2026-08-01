import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Proxies /api straight to the Spring Boot app so the browser sees everything
// as same-origin during local dev (npm run dev). The Docker/nginx path
// (frontend/nginx.conf) does the equivalent proxying for the built app.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: "http://localhost:8083", changeOrigin: true },
    },
  },
});