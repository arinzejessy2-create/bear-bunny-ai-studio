import os, uuid, json, asyncio, traceback
from pathlib import Path
from typing import Dict, Any
from contextlib import suppress

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, HTMLResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from pipeline import run_production

BASE = Path(__file__).parent
OUT = BASE / "outputs"
OUT.mkdir(exist_ok=True)

JOBS: Dict[str, Dict[str, Any]] = {}
QUEUE: asyncio.Queue = asyncio.Queue()
QUEUE_TASK = None

app = FastAPI(title="Bear & Bunny AI Studio", version="0.3.0")
app.mount("/static", StaticFiles(directory=BASE / "static"), name="static")


class CreateRequest(BaseModel):
    idea: str = Field(min_length=8, max_length=2000)
    duration_minutes: int = Field(default=3, ge=1, le=10)
    aspect_ratio: str = Field(default="16:9")
    style: str = Field(default="storybook illustration")
    language: str = Field(default="English")
    voice_style: str = Field(default="warm, playful storyteller")
    subtitles: bool = True
    make_thumbnail: bool = True


def state_path(job_id: str) -> Path:
    return OUT / job_id / "state.json"


def save_state(job_id: str) -> None:
    folder = OUT / job_id
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / "state.json"
    temp = folder / "state.json.tmp"
    temp.write_text(json.dumps(JOBS[job_id], ensure_ascii=False), encoding="utf-8")
    temp.replace(target)


def public_state(job: Dict[str, Any]) -> Dict[str, Any]:
    return {k: v for k, v in job.items() if k not in ("result", "error", "request")}


@app.get("/", response_class=HTMLResponse)
def home():
    return (BASE / "static" / "index.html").read_text(encoding="utf-8")


async def process_job(job_id: str) -> None:
    job = JOBS[job_id]
    job.update(status="running", stage="Starting", message="Starting your production...")
    save_state(job_id)

    def progress(stage, pct, msg):
        job.update(stage=stage, progress=pct, message=msg)
        save_state(job_id)

    try:
        result = await run_production(job_id, job["request"], OUT, progress)
        job.update(status="completed", progress=100, stage="Complete",
                    message="Your production is ready.", result=result)
        save_state(job_id)
    except Exception as exc:
        traceback.print_exc()
        job.update(status="failed", stage="Failed",
                    message=f"Production failed: {str(exc)[-1200:]}", error=str(exc))
        save_state(job_id)


async def queue_worker():
    while True:
        job_id = await QUEUE.get()
        try:
            if job_id in JOBS and JOBS[job_id].get("status") in ("queued", "running"):
                await process_job(job_id)
        finally:
            QUEUE.task_done()


@app.on_event("startup")
async def startup():
    global QUEUE_TASK
    # Recover queued or interrupted jobs after an ordinary process restart.
    for folder in OUT.iterdir():
        if not folder.is_dir():
            continue
        path = folder / "state.json"
        if not path.exists():
            continue
        try:
            saved = json.loads(path.read_text(encoding="utf-8"))
            job_id = folder.name
            JOBS[job_id] = saved
            if saved.get("status") in ("queued", "running"):
                saved.update(status="queued", stage="Reconnecting",
                             message="The server restarted. Resuming your video...")
                save_state(job_id)
                QUEUE.put_nowait(job_id)
        except Exception:
            traceback.print_exc()
    QUEUE_TASK = asyncio.create_task(queue_worker())


@app.on_event("shutdown")
async def shutdown():
    global QUEUE_TASK
    if QUEUE_TASK:
        QUEUE_TASK.cancel()
        with suppress(asyncio.CancelledError):
            await QUEUE_TASK


@app.post("/api/create")
async def create(req: CreateRequest):
    job_id = str(uuid.uuid4())
    JOBS[job_id] = {
        "status": "queued",
        "progress": 0,
        "stage": "Queued",
        "message": "Your video is in the production queue...",
        "result": None,
        "request": req.model_dump()
    }
    save_state(job_id)
    QUEUE.put_nowait(job_id)
    return {"job_id": job_id}


@app.get("/api/status/{job_id}")
def status(job_id: str):
    if job_id not in JOBS:
        # Try disk in case this worker loaded a job after a restart.
        path = state_path(job_id)
        if path.exists():
            JOBS[job_id] = json.loads(path.read_text(encoding="utf-8"))
        else:
            raise HTTPException(404, "Production not found. It may have been cleared by a service redeploy.")
    return public_state(JOBS[job_id])


@app.get("/api/result/{job_id}")
def result(job_id: str):
    if job_id not in JOBS:
        path = state_path(job_id)
        if path.exists():
            JOBS[job_id] = json.loads(path.read_text(encoding="utf-8"))
    if job_id not in JOBS:
        raise HTTPException(404, "Production not found")
    r = JOBS[job_id].get("result")
    if not r:
        raise HTTPException(409, "Production is not ready")
    return r


@app.get("/api/download/{job_id}")
def download(job_id: str):
    if job_id not in JOBS:
        path = state_path(job_id)
        if path.exists():
            JOBS[job_id] = json.loads(path.read_text(encoding="utf-8"))
    if job_id not in JOBS:
        raise HTTPException(404, "Production not found")
    r = JOBS[job_id].get("result")
    if not r or not r.get("video_path"):
        raise HTTPException(404, "Video not ready")
    video = Path(r["video_path"])
    if not video.exists():
        raise HTTPException(404, "Video file missing. The free host may have restarted or redeployed.")
    return FileResponse(video, media_type="video/mp4", filename="bear-and-bunny-adventure.mp4")


@app.get("/health")
def health():
    return {"ok": True, "app": "Bear & Bunny AI Studio", "queue": QUEUE.qsize()}
