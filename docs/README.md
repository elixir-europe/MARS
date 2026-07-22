# MARS Docs

This directory contains the Astro project for the MARS documentation site. Run the npm commands from this `docs/` directory, not from the repository root.

## Prerequisites

- Node.js LTS
- npm

## Install

```sh
cd docs
npm install
```

## Run Locally

Start the Astro development server:

```sh
cd docs
npm run dev
```

Astro prints the local URL in the terminal. Open that URL in a browser.

## Build And Preview A Local Deployment

Build the production site:

```sh
cd docs
npm run build
```

Preview the built output locally:

```sh
cd docs
npm run preview
```

The Astro config uses `base: '/MARS/'`, matching the GitHub Pages deployment path. When previewing the production build, open:

```text
http://localhost:4321/MARS/
```

The generated static site is written to `dist/`.

## Useful Commands

```sh
npm run dev      # local development server
npm run build    # production build
npm run preview  # serve the built site locally
```
