import os, json, subprocess, asyncio, re, time
from pathlib import Path
from typing import Callable
import httpx

def run(cmd):
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode:
        raise RuntimeError((p.stderr or p.stdout)[-2500:])
    return p.stdout

async def generate_story(idea, req):
    """Uses an OpenAI-compatible chat endpoint when configured."""
    base = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    key = os.getenv("OPENAI_API_KEY")
    model = os.getenv("TEXT_MODEL", "gpt-4o-mini")
    if not key:
        return {
          "title":"Bear & Bunny and the Mysterious Door",
          "description":"Join Bear and Bunny on a magical forest adventure.",
          "hashtags":["#BearAndBunny","#KidsStories","#3DAnimation"],
          "scenes":[
            {"n":1,"duration":12,"narration":"One bright morning, Bear and Bunny explored a quiet forest. Then they noticed something strange between two ancient trees.","visual":"Wide cinematic shot of a lush enchanted forest, cute original brown bear and cream bunny walking together, a mysterious glowing door between ancient trees."},
            {"n":2,"duration":12,"narration":"A tiny golden light danced around the door. Bunny stepped closer, while Bear held the map tightly.","visual":"Medium shot, cute cream bunny curiously approaching a small magical wooden door, friendly brown bear holding a folded map, warm golden particles."},
            {"n":3,"duration":12,"narration":"The door opened with a gentle sparkle. Beyond it was a garden floating among the clouds.","visual":"The magical door opening to reveal a breathtaking floating garden in the clouds, Bear and Bunny reacting with wonder, cinematic 3D family animation."},
            {"n":4,"duration":12,"narration":"Together, the friends crossed the doorway and discovered that the greatest magic was sharing an adventure.","visual":"Bear and Bunny holding paws and stepping into a colourful floating garden, joyful emotional ending, cinematic wide shot."}
          ]
        }
    prompt = f"""Create a child-safe {req['duration_minutes']}-minute story for Bear & Bunny Adventures.
Idea: {idea}
Language: {req['language']}. Style: {req['style']}.
Bear is a gentle, curious small brown bear. Bunny is a brave, playful cream-white rabbit.
Keep their appearance, clothing and personalities consistent in every scene. No scary violence,
no dangerous instructions, no copyrighted characters. Produce 8-20 scenes with short narration
and a detailed visual prompt per scene. Return ONLY JSON:
{{"title":"...","description":"...","hashtags":["..."],"scenes":[{{"n":1,"duration":10,"narration":"...","visual":"..."}}]}}
The total scene duration should be close to {req['duration_minutes']*60} seconds."""
    async with httpx.AsyncClient(timeout=180) as c:
        r = await c.post(base+"/chat/completions", headers={"Authorization":f"Bearer {key}"},
          json={"model":model,"messages":[{"role":"user","content":prompt}],"temperature":0.8})
        r.raise_for_status()
        content = r.json()["choices"][0]["message"]["content"]
    content = re.sub(r"^\x60\x60\x60(?:json)?|\x60\x60\x60$", "", content.strip(), flags=re.M).strip()
    data = json.loads(content)
    if not data.get("scenes"): raise RuntimeError("The story model returned no scenes.")
    return data

async def make_voice(text, destination, voice):
    key = os.getenv("OPENAI_API_KEY")
    if not key:
        raise RuntimeError("Narration needs OPENAI_API_KEY or a configured self-hosted TTS service. See SETUP.md.")
    base = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    async with httpx.AsyncClient(timeout=300) as c:
        r = await c.post(base+"/audio/speech", headers={"Authorization":f"Bearer {key}"},
          json={"model":os.getenv("TTS_MODEL","gpt-4o-mini-tts"),"voice":os.getenv("TTS_VOICE","coral"),
                "input":text,"instructions":voice,"response_format":"mp3"})
        r.raise_for_status()
        Path(destination).write_bytes(r.content)

async def render_scene_comfy(prompt, destination, scene_index):
    server = os.getenv("COMFYUI_URL","").rstrip("/")
    workflow_file = os.getenv("COMFYUI_WORKFLOW","workflows/wan_api_workflow.json")
    if not server:
        raise RuntimeError("Animation engine is not connected. Add a ComfyUI server and Wan workflow; see SETUP.md.")
    wf_path = Path(workflow_file)
    if not wf_path.is_absolute(): wf_path = Path(__file__).parent / wf_path
    if not wf_path.exists():
        raise RuntimeError(f"Wan workflow file not found: {wf_path}. Export an API-format workflow from ComfyUI.")
    workflow = json.loads(wf_path.read_text())
    node_id = os.getenv("WAN_PROMPT_NODE_ID")
    input_name = os.getenv("WAN_PROMPT_INPUT","text")
    if not node_id or node_id not in workflow:
        raise RuntimeError("Set WAN_PROMPT_NODE_ID to the text-prompt node in your ComfyUI workflow.")
    workflow[node_id]["inputs"][input_name] = prompt
    async with httpx.AsyncClient(timeout=60) as c:
        queued = await c.post(server+"/prompt", json={"prompt":workflow})
        queued.raise_for_status()
        prompt_id = queued.json()["prompt_id"]
        for _ in range(1800):
            await asyncio.sleep(2)
            hist = await c.get(server+f"/history/{prompt_id}")
            hist.raise_for_status()
            item = hist.json().get(prompt_id)
            if item and item.get("outputs"):
                for node in item["outputs"].values():
                    for key in ("videos","gifs"):
                        for entry in node.get(key,[]):
                            params = {"filename":entry["filename"],"subfolder":entry.get("subfolder",""),
                                      "type":entry.get("type","output")}
                            media = await c.get(server+"/view",params=params)
                            media.raise_for_status()
                            Path(destination).write_bytes(media.content)
                            return
        raise RuntimeError("Wan generation timed out. Check the ComfyUI server and GPU.")

def srt_time(seconds):
    ms = int(seconds*1000); h=ms//3600000; ms%=3600000; m=ms//60000; ms%=60000; s=ms//1000; ms%=1000
    return f"{h:02}:{m:02}:{s:02},{ms:03}"

def make_srt(scenes, path):
    t=0; lines=[]
    for i,sc in enumerate(scenes,1):
        d=float(sc.get("duration",10))
        lines += [str(i), f"{srt_time(t)} --> {srt_time(t+d)}", sc["narration"], ""]
        t += d
    Path(path).write_text("\n".join(lines), encoding="utf-8")

async def run_production(job_id, req, out_root: Path, progress: Callable):
    job = out_root / job_id
    job.mkdir(parents=True, exist_ok=True)
    progress("Writing story",5,"Writing the adventure and planning scenes...")
    story = await generate_story(req["idea"], req)
    (job/"story.json").write_text(json.dumps(story,indent=2),encoding="utf-8")
    scenes = story["scenes"]
    progress("Narration",12,"Creating character narration...")
    narration = " ".join(s["narration"] for s in scenes)
    await make_voice(narration, job/"narration.mp3", req["voice_style"])
    progress("Animation",20,"Sending scene prompts to the connected Wan animation engine...")
    clips=[]
    for i,sc in enumerate(scenes,1):
        progress("Animation",20+int(55*i/len(scenes)),f"Animating scene {i} of {len(scenes)}...")
        p = f"{req['style']}. {sc['visual']} Consistent characters: Bear, a gentle small brown bear; Bunny, a playful cream-white rabbit. Same character designs across all scenes. Child-friendly."
        clip=job/f"scene_{i:02}.mp4"
        await render_scene_comfy(p,clip,i)
        clips.append(clip)
    progress("Editing",80,"Assembling scenes, narration and subtitles...")
    concat=job/"clips.txt"
    concat.write_text("\n".join(f"file '{p.as_posix()}'" for p in clips))
    silent=job/"joined.mp4"
    run(["ffmpeg","-y","-f","concat","-safe","0","-i",str(concat),"-c","copy",str(silent)])
    final=job/"bear_bunny_final.mp4"
    if req.get("subtitles"):
        make_srt(scenes,job/"captions.srt")
        vf=f"subtitles={str(job/'captions.srt').replace(':','\\\\:')}:force_style='FontName=DejaVu Sans,FontSize=22,PrimaryColour=&H00FFFFFF,OutlineColour=&H00101010,BorderStyle=1,Outline=3,Shadow=1,Alignment=2,MarginV=45'"
    else: vf="format=yuv420p"
    run(["ffmpeg","-y","-i",str(silent),"-i",str(job/"narration.mp3"),"-vf",vf,
         "-map","0:v:0","-map","1:a:0","-shortest","-c:v","libx264","-preset","veryfast",
         "-crf","21","-c:a","aac","-b:a","192k","-movflags","+faststart",str(final)])
    progress("Packaging",95,"Preparing YouTube title, description and tags...")
    return {"video_path":str(final),"title":story.get("title","Bear & Bunny Adventures"),
            "description":story.get("description",""),"hashtags":story.get("hashtags",[]),
            "story_path":str(job/"story.json"),"note":"Thumbnail generation and direct YouTube upload are not enabled in this build."}
