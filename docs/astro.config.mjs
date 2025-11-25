// @ts-check
import { defineConfig } from 'astro/config';

import tailwindcss from '@tailwindcss/vite';

// https://astro.build/config
export default defineConfig({
  base: '/MARS',
  trailingSlash: 'never',
  vite: {
    plugins: [tailwindcss()]
  }
})