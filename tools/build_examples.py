"""Merge tools/examples/*.tsv (id, German sentence, English) into app assets/examples.json."""
import glob, json, os, sys

root = os.path.dirname(os.path.abspath(__file__))
out = {}
for f in sorted(glob.glob(os.path.join(root, "examples", "*.tsv"))):
    for line in open(f, encoding="utf-8"):
        parts = line.rstrip("\n").split("\t")
        if len(parts) == 3 and parts[1]:
            out[parts[0] + ".webm"] = [parts[1], parts[2]]
dst = os.path.join(root, "..", "app", "src", "main", "assets", "examples.json")
json.dump(out, open(dst, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print(len(out), "examples")
