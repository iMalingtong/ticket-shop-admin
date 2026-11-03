import { getPluginsList } from "./build/plugins";
import { include, exclude } from "./build/optimize";
import { defineConfig } from "vite";
import { alias, __APP_INFO__ } from "./build/utils";

const apiTarget =
  process.env.TICKET_SHOP_DEV_API_TARGET || "http://127.0.0.1:18080";
if (
  !/^http:\/\/127\.0\.0\.1:[0-9]{1,5}$/.test(apiTarget) ||
  Number(new URL(apiTarget).port) < 1 ||
  Number(new URL(apiTarget).port) > 65535
)
  throw new Error("开发代理仅允许本机127.0.0.1端口");

export default defineConfig({
  envDir: false,
  base: "/",
  resolve: { alias },
  server: {
    host: "127.0.0.1",
    port: 8848,
    strictPort: true,
    proxy: {
      "/api/health": {
        target: apiTarget,
        rewrite: path => path.replace(/^\/api\/health/, "/actuator/health")
      },
      "/api": {
        target: apiTarget
      }
    }
  },
  plugins: getPluginsList(false, "none"),
  optimizeDeps: { include, exclude },
  build: { target: "es2022", sourcemap: false, chunkSizeWarningLimit: 2500 },
  define: {
    __INTLIFY_PROD_DEVTOOLS__: false,
    __APP_INFO__: JSON.stringify(__APP_INFO__),
    "import.meta.env.VITE_PUBLIC_PATH": JSON.stringify("/"),
    "import.meta.env.VITE_ROUTER_HISTORY": JSON.stringify("h5"),
    "import.meta.env.VITE_HIDE_HOME": JSON.stringify("false")
  }
});
