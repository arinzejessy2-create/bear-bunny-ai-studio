# Bear & Bunny AI Studio

A mobile-first web app scaffold for a one-prompt children's animation production workflow.

## What this build does
- Mobile-friendly prompt interface and production progress screen
- Story/scene planning through an OpenAI-compatible chat endpoint
- Narration generation through an OpenAI-compatible speech endpoint
- ComfyUI integration point for a user-supplied Wan text-to-video workflow
- FFmpeg assembly and subtitle burn-in
- YouTube title, description and hashtag packaging

## Important: this is not a turnkey hosted AI service
The animation engine must be connected to a GPU-powered ComfyUI server with a compatible Wan workflow. API usage, GPU hosting and some model licences may involve costs. This build does not provide unlimited free generation, direct YouTube upload, automatic thumbnail creation, music generation, or verified word-level subtitle alignment. Captions are currently scene-level. Do not publish children's content without reviewing it.

See `SETUP.md` for configuration.
