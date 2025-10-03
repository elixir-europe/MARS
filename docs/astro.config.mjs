// @ts-check
import { defineConfig } from 'astro/config';

import tailwindcss from '@tailwindcss/vite';

// https://astro.build/config
export default defineConfig({
  site: 'https://elixir-europe.github.io',
  base: '/MARS',

  vite: {
    plugins: [tailwindcss()]
  }
})