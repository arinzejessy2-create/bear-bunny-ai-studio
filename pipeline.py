import os, json, subprocess, asyncio, re, textwrap, math
import httpx
from pathlib import Path
from typing import Callable
from PIL import Image, ImageDraw, ImageFont
from gtts import gTTS
from audio_design import make_sound_bed, make_karaoke_ass

def run(cmd):
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode:
        raise RuntimeError((p.stderr or p.stdout)[-2500:])
    return p.stdout

def font(size, bold=False):
    paths = ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]
    for p in paths:
        if Path(p).exists(): return ImageFont.truetype(p, size)
    return ImageFont.load_default()

async def generate_story(idea, req):
    base = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    key = os.getenv("OPENAI_API_KEY")
    if key:
        prompt = f"""Create a child-safe {req['duration_minutes']}-minute story for Bear & Bunny Adventures. Idea: {idea}. Language: {req['language']}. Bear is a gentle small brown bear; Bunny is a brave cream-white rabbit. Keep them consistent. Return ONLY JSON with title, description, hashtags, and scenes (each has n,duration,narration,visual). 4-8 scenes."""
        async with httpx.AsyncClient(timeout=120) as c:
            r = await c.post(base+"/chat/completions",headers={"Authorization":f"Bearer {key}"},json={"model":os.getenv("TEXT_MODEL","gpt-4o-mini"),"messages":[{"role":"user","content":prompt}],"temperature":0.8})
            r.raise_for_status()
            raw=r.json()["choices"][0]["message"]["content"]
        raw=re.sub(r"^\x60\x60\x60(?:json)?|\x60\x60\x60$","",raw.strip(),flags=re.M).strip()
        return json.loads(raw)
    subject = idea.strip().rstrip(".!?")
    scenes = [
      {"n":1,"duration":8,"narration":f"One sunny morning, Bear and Bunny set off on an adventure. They had heard a curious story about {subject}, and they couldn't wait to find out more.","visual":"sunny beginning"},
      {"n":2,"duration":8,"narration":"Along the winding forest path, they found a little clue. Bear wanted to think carefully, but Bunny was already hopping ahead with excitement.","visual":"discovering a clue"},
      {"n":3,"duration":8,"narration":"Suddenly, something unexpected happened. The friends worked together, shared their ideas, and discovered that being brave is easier when you help each other.","visual":"teamwork and discovery"},
      {"n":4,"duration":8,"narration":"As the sun began to set, Bear and Bunny headed home smiling. Their biggest treasure wasn't what they found. It was the wonderful adventure they shared.","visual":"happy sunset ending"}
    ]
    return {"title":"Bear & Bunny: "+subject[:55],"description":"A cheerful Bear & Bunny adventure about curiosity, courage and friendship.","hashtags":["#BearAndBunny","#KidsStories","#Friendship"],"scenes":scenes}

async def make_voice(text, destination, voice):
    # Try free online TTS first; if the network service is unavailable,
    # fall back to the offline espeak-ng voice included in the container.
    try:
        gTTS(text=text, lang="en", slow=False).save(str(destination))
        return
    except Exception:
        wav = Path(destination).with_suffix(".wav")
        subprocess.run(["espeak-ng", "-v", "en", "-s", "155", "-w", str(wav), text],
                       check=True, capture_output=True)
        run(["ffmpeg", "-y", "-i", str(wav), "-codec:a", "libmp3lame", "-q:a", "4", str(destination)])
        wav.unlink(missing_ok=True)

def draw_scene(path, index, idea, scene):
    W,H=1280,720
    palettes=[("#86c8ed","#e8f5ff"),("#79b8a0","#d9efc9"),("#f4bd70","#fce7b8"),("#e98f72","#f8d5a9")]
    top,bottom=palettes[(index-1)%len(palettes)]
    im=Image.new("RGB",(W,H)); pix=im.load()
    import PIL.ImageColor as IC
    a,b=IC.getrgb(top),IC.getrgb(bottom)
    for y in range(H):
        t=y/(H-1); col=tuple(int(a[k]*(1-t)+b[k]*t) for k in range(3))
        for x in range(W): pix[x,y]=col
    d=ImageDraw.Draw(im)
    # rolling hills, trees and storybook scenery
    d.ellipse((-180,420,700,900),fill="#72a96b"); d.ellipse((500,390,1450,900),fill="#8fbd76")
    for x,y,s in [(130,360,1),(1080,340,.9),(950,420,.65),(270,430,.6)]:
        d.rectangle((int(x-12*s),int(y),int(x+12*s),650),fill="#80563b")
        d.ellipse((int(x-75*s),int(y-100*s),int(x+75*s),int(y+55*s)),fill="#397d55")
        d.ellipse((int(x-55*s),int(y-145*s),int(x+55*s),int(y-30*s)),fill="#4b9561")
    # glowing story object
    cx,cy=640,300
    d.ellipse((cx-85,cy-85,cx+85,cy+85),fill="#fff2a8",outline="#fffbd5",width=8)
    d.ellipse((cx-55,cy-55,cx+55,cy+55),fill="#ffd76a")
    # bear character
    def bear(x,y):
        d.ellipse((x-78,y-160,x-22,y-104),fill="#87502f",outline="#4e3024",width=5)
        d.ellipse((x+22,y-160,x+78,y-104),fill="#87502f",outline="#4e3024",width=5)
        d.ellipse((x-92,y-145,x+92,y+55),fill="#a96c42",outline="#4e3024",width=6)
        d.ellipse((x-57,y-68,x+57,y+15),fill="#d9a77e")
        d.ellipse((x-35,y-105,x-18,y-86),fill="#201b18"); d.ellipse((x+18,y-105,x+35,y-86),fill="#201b18")
        d.ellipse((x-12,y-58,x+12,y-42),fill="#30201a")
        d.arc((x-28,y-55,x+28,y-15),0,180,fill="#57352b",width=4)
        d.rounded_rectangle((x-75,y+35,x+75,y+175),radius=48,fill="#b8794c",outline="#4e3024",width=6)
    def bunny(x,y):
        d.ellipse((x-65,y-245,x-5,y-100),fill="#fff3df",outline="#a99a86",width=5)
        d.ellipse((x+5,y-245,x+65,y-100),fill="#fff3df",outline="#a99a86",width=5)
        d.ellipse((x-88,y-150,x+88,y+55),fill="#fff8e9",outline="#a99a86",width=6)
        d.ellipse((x-55,y-72,x+55,y+10),fill="#efddc6")
        d.ellipse((x-34,y-110,x-18,y-90),fill="#28211d"); d.ellipse((x+18,y-110,x+34,y-90),fill="#28211d")
        d.ellipse((x-10,y-58,x+10,y-44),fill="#e88e9b")
        d.rounded_rectangle((x-72,y+38,x+72,y+175),radius=48,fill="#f4eee4",outline="#a99a86",width=6)
    bear(430,455); bunny(820,455)
    # Caption panel and readable scene title
    d.rounded_rectangle((45,35,1235,145),radius=28,fill="#17334a",outline="#ffffff",width=3)
    title=["A wonderful new day","A curious discovery","Friends solve it together","A happy ending"][index-1]
    d.text((W//2,90),title,font=font(43,True),fill="white",anchor="mm",stroke_width=1,stroke_fill="#29425b")
    d.text((W//2,660),"BEAR & BUNNY ADVENTURES",font=font(22,True),fill="white",anchor="mm",stroke_width=2,stroke_fill="#385b43")
    im.save(path,quality=94)

def srt_time(seconds):
    ms=int(seconds*1000); h=ms//3600000; ms%=3600000; m=ms//60000; ms%=60000; s=ms//1000; ms%=1000
    return f"{h:02}:{m:02}:{s:02},{ms:03}"

def make_srt(scenes,path):
    t=0; lines=[]
    for i,sc in enumerate(scenes,1):
        d=float(sc.get("duration",8)); lines += [str(i),f"{srt_time(t)} --> {srt_time(t+d)}",sc["narration"],""]; t+=d
    Path(path).write_text("\n".join(lines),encoding="utf-8")

async def render_scene_comfy(prompt,destination,scene_index):
    raise RuntimeError("The free storybook renderer is active. Paid or self-hosted GPU animation is not required.")

async def run_production(job_id,req,out_root:Path,progress:Callable):
    job=out_root/job_id; job.mkdir(parents=True,exist_ok=True)
    progress("Writing story",5,"Creating your story without paid AI services...")
    story=await generate_story(req["idea"],req)
    scenes=story["scenes"]
    target_seconds=float(req.get("duration_minutes",3))*60
    raw=[max(1.0,float(s.get("duration",8))) for s in scenes]
    scale=target_seconds/sum(raw)
    for s,d in zip(scenes,raw): s["duration"]=round(d*scale,2)
    if scenes: scenes[-1]["duration"]=round(target_seconds-sum(float(s["duration"]) for s in scenes[:-1]),2)
    (job/"story.json").write_text(json.dumps(story,indent=2),encoding="utf-8")
    progress("Narration",15,"Creating free narration...")
    narration=" ".join(s["narration"] for s in scenes)
    await make_voice(narration,job/"narration.mp3",req.get("voice_style","warm storyteller"))
    clips=[]
    for i,sc in enumerate(scenes,1):
        progress("Illustration",20+int(48*i/len(scenes)),f"Drawing Bear & Bunny scene {i} of {len(scenes)}...")
        image=job/f"scene_{i:02}.jpg"; draw_scene(image,i,req["idea"],sc)
        clip=job/f"scene_{i:02}.mp4"
        size="720:1280" if req.get("aspect_ratio")=="9:16" else "854:480"
        vf=f"scale={size}:force_original_aspect_ratio=increase,crop={size},zoompan=z='min(zoom+0.0005,1.04)':d=1:s={size}:fps=24,format=yuv420p"
        run(["ffmpeg","-y","-filter_threads","1","-threads","1","-loop","1","-i",str(image),"-t",str(sc.get("duration",8)),"-vf",vf,"-r","24","-c:v","libx264","-threads","1","-preset","ultrafast","-crf","27",str(clip)])
        clips.append(clip)
    progress("Editing",75,"Adding narration, movement and subtitles...")
    concat=job/"clips.txt"; concat.write_text("\n".join(f"file '{p.as_posix()}'" for p in clips))
    silent=job/"joined.mp4"
    run(["ffmpeg","-y","-f","concat","-safe","0","-i",str(concat),"-c","copy",str(silent)])
    final=job/"bear_bunny_final.mp4"
    music=job/"original_music.wav"; sfx=job/"scene_sfx.wav"
    make_sound_bed(sum(float(s.get("duration",8)) for s in scenes),music,sfx,scenes)
    if req.get("subtitles"):
        ass=job/"captions.ass"; make_karaoke_ass(scenes,ass)
        vf="ass="+str(ass).replace(":","\\:")+",format=yuv420p"
    else: vf="format=yuv420p"
    mix="[2:a]volume=0.22[music];[music][1:a]sidechaincompress=threshold=0.025:ratio=8:attack=25:release=650[ducked];[ducked][3:a]amix=inputs=2:duration=longest:weights=1 0.55:normalize=0[aout]"
    run(["ffmpeg","-y","-filter_threads","1","-filter_complex_threads","1","-threads","1","-i",str(silent),"-i",str(job/"narration.mp3"),"-i",str(music),"-i",str(sfx),"-filter_complex",mix,"-vf",vf,"-map","0:v:0","-map","[aout]","-shortest","-c:v","libx264","-threads","1","-preset","ultrafast","-crf","26","-c:a","aac","-b:a","128k","-movflags","+faststart",str(final)])
    progress("Packaging",95,"Preparing your YouTube details...")
    return {"video_path":str(final),"title":story.get("title","Bear & Bunny Adventures"),"description":story.get("description",""),"hashtags":story.get("hashtags",[]),"story_path":str(job/"story.json"),"note":"Free storybook-motion mode with original generated background music, scene-aware sound accents, music ducking and bold karaoke-style captions. Cinematic AI footage requires an external GPU service."}
