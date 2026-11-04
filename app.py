import os, uuid, json, asyncio, shutil
from pathlib import Path
from typing import Dict, Any
from fastapi import FastAPI, BackgroundTasks, HTTPException
from fastapi.responses import FileResponse, HTMLResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field
from pipeline import run_production

BASE = Path(__file__).parent
OUT = BASE / "outputs"
OUT.mkdir(exist_ok=True)
JOBS: Dict[str, Dict[str, Any]] = {}

app = FastAPI(title="Bear & Bunny AI Studio", version="0.2.0")
app.mount("/static", StaticFiles(directory=BASE / "static"), name="static")

class CreateRequest(BaseModel):
    idea: str = Field(min_length=8, max_length=2000)
    duration_minutes: int = Field(default=3, ge=1, le=10)
    aspect_ratio: str = Field(default="16:9")
    style: str = Field(default="cinematic 3D family animation")
    language: str = Field(default="English")
    voice_style: str = Field(default="warm, playful storyteller")
    subtitles: bool = True
    make_thumbnail: bool = True

@app.get("/", response_class=HTMLResponse)
def home():
    return (BASE / "static" / "index.html").read_text(encoding="utf-8")

async def worker(job_id: str, req: dict):
    try:
        JOBS[job_id]["status"] = "running"
        result = await run_production(job_id, req, OUT, lambda stage, pct, msg:
            JOBS[job_id].update(stage=stage, progress=pct, message=msg))
        JOBS[job_id].update(status="completed", progress=100, stage="Complete",
                            message="Your production is ready.", result=result)
    except Exception as e:
        JOBS[job_id].update(status="failed", message=str(e), error=str(e))

@app.post("/api/create")
async def create(req: CreateRequest, background: BackgroundTasks):
    job_id = str(uuid.uuid4())
    JOBS[job_id] = {"status":"queued", "progress":0, "stage":"Queued",
                    "message":"Preparing your production...", "result":None}
    background.add_task(worker, job_id, req.model_dump())
    return {"job_id": job_id}

@app.get("/api/status/{job_id}")
def status(job_id: str):
    if job_id not in JOBS: raise HTTPException(404, "Production not found")
    j = JOBS[job_id]
    return {k:v for k,v in j.items() if k not in ("result","error")}

@app.get("/api/result/{job_id}")
def result(job_id: str):
    if job_id not in JOBS: raise HTTPException(404, "Production not found")
    r = JOBS[job_id].get("result")
    if not r: raise HTTPException(409, "Production is not ready")
    return r

@app.get("/api/download/{job_id}")
def download(job_id: str):
    if job_id not in JOBS: raise HTTPException(404, "Production not found")
    r = JOBS[job_id].get("result")
    if not r or not r.get("video_path"): raise HTTPException(404, "Video not ready")
    p = Path(r["video_path"])
    if not p.exists(): raise HTTPException(404, "Video file missing")
    return FileResponse(p, media_type="video/mp4", filename="bear-and-bunny-adventure.mp4")

@app.get("/health")
def health():
    return {"ok": True, "app": "Bear & Bunny AI Studio"}
