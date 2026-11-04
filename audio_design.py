import math, random, wave
from pathlib import Path

def make_sound_bed(duration, music_path, sfx_path, scenes):
    """Create original, royalty-free ambient music and scene-aware accent effects."""
    rate=22050
    total=max(1.0,float(duration))
    rng=random.Random(42)
    # Soft original pad + gentle pulse; keep it intentionally below narration.
    with wave.open(str(music_path),"wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(rate)
        chunk=4096
        for start in range(0,int(total*rate),chunk):
            frames=[]
            for i in range(min(chunk,int(total*rate)-start)):
                t=(start+i)/rate
                fade=min(1,t/1.5,max(0,(total-t)/2))
                chord=(math.sin(2*math.pi*110*t)+.55*math.sin(2*math.pi*164.81*t)+.35*math.sin(2*math.pi*220*t))
                pulse=.75+.25*math.sin(2*math.pi*.16*t)
                v=int(max(-1,min(1,chord*.035*pulse*fade))*32767)
                frames.append((v,v))
            w.writeframes(b"".join(int(a).to_bytes(2,"little",signed=True)+int(b).to_bytes(2,"little",signed=True) for a,b in frames))
    # Soft chime accents only at semantic scene moments; no copyrighted samples.
    events=[]
    for i,sc in enumerate(scenes):
        txt=(str(sc.get("visual",""))+" "+str(sc.get("narration",""))).lower()
        cue=next((word for word in ("discover","magic","myster","surprise","treasure","sparkle","door","star") if word in txt),None)
        if cue: events.append((sum(float(x.get("duration",8)) for x in scenes[:i])+1.0, cue))
    with wave.open(str(sfx_path),"wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(rate)
        length=int(total*rate); buf=bytearray()
        tones=[523.25,659.25,783.99]
        for n in range(length):
            t=n/rate; val=0.0
            for at,cue in events:
                dt=t-at
                if 0<=dt<.65:
                    env=math.exp(-5*dt)
                    val += .10*env*(math.sin(2*math.pi*tones[0]*dt)+.55*math.sin(2*math.pi*tones[1]*dt))
            q=int(max(-1,min(1,val))*32767)
            buf.extend(q.to_bytes(2,"little",signed=True)*2)
        w.writeframes(bytes(buf))

def make_karaoke_ass(scenes, path, word_timings=None):
    """Readable bold karaoke captions; word timing is estimated from scene narration duration."""
    def stamp(sec):
        cs=max(0,int(round(sec*100))); h=cs//360000; cs%=360000
        m=cs//6000; cs%=6000; s=cs//100; cs%=100
        return f"{h}:{m:02}:{s:02}.{cs:02}"
    lines=["[Script Info]","ScriptType: v4.00+","PlayResX: 1280","PlayResY: 720","ScaledBorderAndShadow: yes",
      "[V4+ Styles]","Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
      "Style: Karaoke,DejaVu Sans,54,&H00FFFFFF,&H0000D7FF,&H00101828,&H80000000,-1,0,1,4,2,2,70,70,62,1","[Events]","Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"]
    # Prefer real speech-provider word boundaries; use estimated timing only on fallback voices.
    if word_timings:
        clean=[]
        for item in word_timings:
            word=str(item.get("text","")).strip()
            if word:
                start=max(0.0,float(item.get("start",0)))
                end=max(start+0.04,start+float(item.get("duration",0.12)))
                clean.append((word,start,end))
        for offset in range(0,len(clean),5):
            group=clean[offset:offset+5]
            start=group[0][1]; end=group[-1][2]
            karaoke=" ".join("{\\\\k%d}%s"%(max(1,round((b-a)*100)),w.replace("{","(").replace("}",")")) for w,a,b in group)
            lines.append(f"Dialogue: 0,{stamp(start)},{stamp(end)},Karaoke,,0,0,0,,{karaoke}")
        Path(path).write_text("\\n".join(lines),encoding="utf-8")
        return
    cursor=0.0
    for sc in scenes:
        dur=float(sc.get("duration",8)); words=str(sc.get("narration","")).split()
        if not words: cursor+=dur; continue
        weights=[max(1,len(x.strip(".,!?;:"))) for x in words]; total=sum(weights)
        chunks=[]; group=[]; groupw=0
        for word,weight in zip(words,weights):
            group.append((word,weight)); groupw+=weight
            if len(group)>=5:
                chunks.append((group,groupw)); group=[]; groupw=0
        if group: chunks.append((group,groupw))
        elapsed=0.0
        for group,gw in chunks:
            seg=dur*gw/total
            karaoke=" ".join("{\\k%d}%s"%(max(1,round((dur*weight/total)*100)),word.replace("{","(").replace("}",")")) for word,weight in group)
            lines.append(f"Dialogue: 0,{stamp(cursor+elapsed)},{stamp(cursor+elapsed+seg)},Karaoke,,0,0,0,,{karaoke}")
            elapsed+=seg
        cursor+=dur
    Path(path).write_text("\n".join(lines),encoding="utf-8")
