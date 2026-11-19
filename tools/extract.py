"""Extract English/German pairs from the Collins PDF: one lesson per PDF page."""
import json, re, sys
import pymupdf

src, out = sys.argv[1], sys.argv[2]
doc = pymupdf.open(src)
lessons = []
topic = None
for pno, page in enumerate(doc):
    spans = []
    big = []
    for b in page.get_text("dict")["blocks"]:
        for l in b.get("lines", []):
            for s in l["spans"]:
                t = s["text"].replace("’", "'").replace("…", "...")
                if not t.strip():
                    continue
                if s["size"] > 9:
                    big.append((s["bbox"][0], s["bbox"][1], t)); continue
                spans.append((round(s["bbox"][0] / 20), s["bbox"][1], s, t))
    topics = []
    # merge heading spans: same block of lines within ~14pt vertically
    big.sort(key=lambda b: (b[1], b[0]))
    groups = []
    for x, y, t in big:
        if groups and y - groups[-1]["y2"] < 14:
            g = groups[-1]; g["t"] += (" " if y == g["y2"] else " ") + t; g["y2"] = y
        else:
            groups.append({"x": x, "y": y, "y2": y, "t": t})
    for g in groups:
        h = re.sub(r"\s+", " ", g["t"]).strip()
        if "|" in h:
            spans.append((round(g["x"] / 20), g["y"], {"font": "HEAD", "size": 10}, h))
    # group lines: within a column, sort by y; merge spans on same line
    cards, section = [], ""
    cur_en, cur_de, last_kind = [], [], None
    def flush():
        global cur_en, cur_de
        if cur_en and cur_de:
            en = re.sub(r"\s+", " ", " ".join(cur_en)).strip()
            de = re.sub(r"\s+", " ", " ".join(cur_de)).strip()
            de = re.sub(r"\s*/\s*", " / ", de)
            if not re.fullmatch(r"\d+", en):
                cards.append({"en": en, "de": de, "section": section})
        cur_en, cur_de = [], []
    for col, y, s, t in sorted(spans, key=lambda x: (x[0], x[1])):
        font = s["font"]
        if font == "HEAD":
            flush(); last_kind = None
            en_t, de_t = [x.strip() for x in t.split("|", 1)]
            topic = (en_t, de_t); topics.append(topic); continue
        if "SC" in font:
            flush(); section = t.strip(); last_kind = None; continue
        if "Italic" in font:
            if cur_de: cur_de[-1] += " " + t.strip()  # gender/number note, e.g. mpl
            continue
        kind = "de" if "Bold" in font else "en"
        if kind == "en" and t.strip().isdigit():
            continue
        if kind == "en" and last_kind == "de":
            flush()
        (cur_de if kind == "de" else cur_en).append(t.strip())
        last_kind = kind
    flush()
    if not topics and topic: topics = [topic]
    cards = [c for c in cards if len(c["en"]) < 90 and len(c["de"]) < 120]
    if pno >= 4 and cards:
        lessons.append({"id": len(lessons) + 1, "page": pno + 1,
                        "title": " & ".join(t[0].capitalize() for t in topics) or f"Page {pno+1}",
                        "titleDe": " & ".join(t[1].capitalize() for t in topics), "cards": cards})
for l in lessons:
    for i, c in enumerate(l["cards"]):
        c["audio"] = f"{l['id']:03d}_{i:02d}.webm"
json.dump(lessons, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(len(lessons), "lessons", sum(len(l["cards"]) for l in lessons), "cards")
