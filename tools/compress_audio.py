"""Re-encode edge-tts mp3s to small mono Opus (.webm) files, then delete the mp3s."""
import os, subprocess, sys
from concurrent.futures import ThreadPoolExecutor

d = sys.argv[1]
def conv(name):
    src = os.path.join(d, name); dst = src[:-4] + ".webm"
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-ac", "1", "-c:a", "libopus",
                        "-b:a", "20k", "-application", "voip", dst])
    if r.returncode == 0: os.remove(src)
    else: print("FAILED", name)
with ThreadPoolExecutor(os.cpu_count()) as ex:
    list(ex.map(conv, [f for f in os.listdir(d) if f.endswith(".mp3")]))
