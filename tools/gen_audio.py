"""Generate German pronunciation mp3s with edge-tts for every card (skips existing files)."""
import asyncio, json, os, re, sys
import edge_tts

lessons_path, out_dir = sys.argv[1], sys.argv[2]
VOICE = os.environ.get("TTS_VOICE", "de-DE-KatjaNeural")
os.makedirs(out_dir, exist_ok=True)

def speakable(de: str) -> str:
    de = re.sub(r"\b(mpl|fpl|npl|m|f|n|pl|sg)\b\.?$", "", de.strip())
    de = re.sub(r"\s(mpl|fpl|npl)\b", "", de)
    return de.replace(" / ", ", ").replace("...", "…").strip()

async def one(sem, text, path):
    async with sem:
        for attempt in range(4):
            try:
                await edge_tts.Communicate(text, VOICE, rate="-10%").save(path + ".tmp")
                os.replace(path + ".tmp", path)
                return
            except Exception as e:
                await asyncio.sleep(2 * (attempt + 1))
        print("FAILED", path, text)

async def main():
    sem = asyncio.Semaphore(8)
    jobs = []
    for l in json.load(open(lessons_path, encoding="utf-8")):
        for c in l["cards"]:
            p = os.path.join(out_dir, c["audio"].rsplit(".", 1)[0] + ".mp3")
            if not os.path.exists(os.path.join(out_dir, c["audio"])) and not os.path.exists(p):
                jobs.append(one(sem, speakable(c["de"]), p))
    print(len(jobs), "files to generate")
    await asyncio.gather(*jobs)

asyncio.run(main())
# compress to Opus
import subprocess
subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "compress_audio.py"), out_dir], check=True)
