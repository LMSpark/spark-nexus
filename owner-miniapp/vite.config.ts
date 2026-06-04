import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

const uniPlugin = typeof uni === 'function' ? uni : (uni as unknown as { default: typeof uni }).default

export default defineConfig({
  plugins: [uniPlugin()],
  server: {
    proxy: {
      '/api': 'http://localhost:8580'
    }
  }
})
