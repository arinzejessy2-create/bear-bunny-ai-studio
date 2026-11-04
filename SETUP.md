# Setup guide

## 1. What you need
- A computer or hosted GPU machine to run ComfyUI and a compatible Wan video workflow. An Android phone can open the studio UI, but it cannot realistically run this video-generation workload.
- Python 3.10+
- FFmpeg installed and available on PATH
- An OpenAI-compatible API key for the current story and narration adapters, or replace those adapters with self-hosted models.
- A Wan workflow exported from ComfyUI in API format.

## 2. Install the studio
```bash
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```
Fill .env with your credentials and service URLs. Never publish .env or put API keys in the browser.

## 3. Connect Wan through ComfyUI
1. Install ComfyUI on a suitable GPU host and install a Wan 2.2 workflow/model supported by that host.
2. Open the workflow in ComfyUI.
3. Export the workflow in API format (not the regular UI workflow format).
4. Save it as workflows/wan_api_workflow.json.
5. Find the node ID and input name that receive the positive text prompt.
6. Set WAN_PROMPT_NODE_ID and WAN_PROMPT_INPUT in .env.
7. Set COMFYUI_URL to the ComfyUI server address reachable from the studio backend.

The workflow must output a video in ComfyUI's videos or gifs output collection. Different workflows use different node IDs, inputs and output formats; this adapter intentionally requires a matching workflow. Do not expose ComfyUI publicly without authentication and network restrictions.

## 4. Run the app
```bash
uvicorn app:app --host 0.0.0.0 --port 8000
```
Open http://YOUR_SERVER_IP:8000 on your phone. For internet access, deploy behind HTTPS and authentication. Do not expose the development server directly to the public internet.

## 5. Before real production
- Test a single short scene before attempting a 3–5 minute story.
- Confirm that the Wan workflow outputs a playable MP4.
- Configure persistent job storage and a task queue for production deployment; this demo stores job state in memory and loses it on restart.
- Add authentication, per-user quotas, secret management, monitoring and cleanup policies.
- Add character reference-image conditioning / IP-Adapter or equivalent to improve identity consistency. Text prompts alone cannot guarantee identical characters.
- Add a licensed music and sound-effects provider or original audio generation. Music/SFX are not connected in this version.
- Add WhisperX or another forced aligner to generate accurate word-level captions. Current captions are timed per scene and may not match narration precisely.
- Add thumbnail generation and YouTube Data API OAuth only after testing and obtaining explicit channel authorization.

## 6. Cost and licensing
The app code is a starter project, not a promise of unlimited free compute. GPU inference, storage, bandwidth and API calls may cost money. Check current licence and commercial-use terms for every model, checkpoint, LoRA, voice and audio asset before monetizing a channel.
