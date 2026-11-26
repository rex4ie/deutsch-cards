"""Build app/src/main/assets/verbs.json for the verb-forms game.

Sources (marked in "src"): c = Collins deck (lessons.json), k = Class deck (my_lessons.json),
f = frequent verbs (tools/verbs/common.tsv). Forms come from german-verbs-dict
(LanguageTool / Morphy data, CC-BY-SA 4.0): `npm pack german-verbs-dict` and pass the path
of dist/verbs.json. Missing verbs or corrections go in tools/verbs/overrides.tsv.

    python tools/build_verbs.py path/to/verbs.json

Ids are the infinitive, so the order of entries can change freely without losing progress.
"""
import json, re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
DICT = json.load(open(sys.argv[1], encoding="utf-8"))
PERSONS = ["ich", "du", "er", "wir", "ihr", "sie"]
INSEP = ("be", "ge", "er", "ver", "zer", "ent", "emp", "miss")


def tsv(name):
    p = ROOT / "tools/verbs" / name
    if not p.exists():
        return []
    return [l.rstrip("\n").split("\t") for l in open(p, encoding="utf-8") if l.strip() and not l.startswith("#")]


common = {}
for inf, aux, en in tsv("common.tsv"):
    common.setdefault(inf, (aux, en))
# overrides: inf, aux, en, prt (er form, e.g. "fuhr ab"), pp, [prt wir form]
overrides = {r[0]: r for r in tsv("overrides.tsv")}


def one(x):
    return " ".join([x[0], x[1]]) if isinstance(x, list) else x


def derive(er):
    """All persons from the er form: 'fuhr ab' -> fuhrst ab, fuhren ab …; 'machte' -> machtest …"""
    head, *tail = er.split(" ")
    rest = (" " + " ".join(tail)) if tail else ""
    if head.endswith("e"):
        ends = {"ich": "", "du": "st", "er": "", "wir": "n", "ihr": "t", "sie": "n"}
    else:
        du = "est" if head[-1] in "tdsßz" else "st"
        ends = {"ich": "", "du": du, "er": "", "wir": "en", "ihr": "et" if head[-1] in "td" else "t", "sie": "en"}
    return {p: head + ends[p] + rest for p in PERSONS}


def forms(inf):
    """(prt per person, participle) or None."""
    if inf in overrides and len(overrides[inf]) > 3 and overrides[inf][3]:
        r = overrides[inf]
        prt = derive(r[3])
        if len(r) > 5 and r[5]:
            prt["wir"] = prt["sie"] = r[5]
        return prt, r[4]
    e = DICT.get(inf)
    if not e or "PRT" not in e or not e.get("PA2"):
        return None
    pp = e["PA2"][0]
    t = e["PRT"]
    keys = {"ich": ("S", "1"), "du": ("S", "2"), "er": ("S", "3"), "wir": ("P", "1"), "ihr": ("P", "2"), "sie": ("P", "3")}
    prt = {p: t.get(n, {}).get(k) for p, (n, k) in keys.items()}
    if prt["er"] is None:
        return None
    if any(v is None for v in prt.values()):
        er = prt["er"]
        d = derive(" ".join([er[0], er[1]]) if isinstance(er, list) else er)
        prt = {p: (v if v is not None else (d[p].split(" ") if isinstance(er, list) else d[p])) for p, v in prt.items()}
    if isinstance(prt["er"], list):
        prefix = prt["er"][1]
        # the dictionary splits every prefix; über-/unter-/um-/durch-… verbs without "ge" in the
        # participle are inseparable (überholen -> überholte, hat überholt)
        insep = pp.startswith(prefix) and not pp[len(prefix):].startswith("ge") and not inf.endswith("ieren")
        prt = {p: (v[1] + v[0] if insep else f"{v[0]} {v[1]}") for p, v in prt.items()}
    return prt, pp


def modern(prt, pp):
    """Old-spelling ß after a short vowel -> ss: schloß -> schloss, paßte -> passte (but aß, saß).
    The participle tells whether the vowel is short: geschlossen vs. gegessen."""
    i = pp.find("ss")
    if i < 1:
        return prt
    vowel = pp[i - 1]
    out = {}
    for p, f in prt.items():
        j = f.find("ß")
        out[p] = f[:j] + "ss" + f[j + 1:] if j > 0 and f[j - 1] == vowel else f
    return out


def base_aux(inf):
    """Guess hat/ist for a verb not in common.tsv from its base verb (ankommen -> kommen)."""
    if inf in common:
        return common[inf][0]
    e = DICT.get(inf, {})
    pre = e.get("PRT", {}).get("S", {}).get("3")
    if isinstance(pre, list):
        base = inf[len(pre[1]):]
        if base in common and not any(inf.startswith(p) for p in INSEP):
            return common[base][0]
    return "h"


def regular(inf, prt, pp):
    stem = inf[:-2] if inf.endswith("en") else inf[:-1]
    stem = stem.split(" ")[-1]
    er = prt["er"].split(" ")[0]
    return er.endswith("te") and pp.endswith("t") and (er.endswith(stem[-3:] + "te") or er.endswith(stem[-3:] + "ete"))


verbs = {}
missing = []


def add(inf, en, src, aux=None, context=""):
    inf = inf.strip()
    if inf in verbs:
        v = verbs[inf]
        if src not in v["src"]:
            v["src"] += src
        if context and not v.get("ctx"):
            v["ctx"] = context
        return
    if inf in common:
        en = common[inf][1]
    elif inf in overrides and overrides[inf][2]:
        en = overrides[inf][2]
    f = forms(inf)
    if f is None:
        missing.append((inf, en, src))
        return
    prt, pp = f
    prt = modern(prt, pp)
    a = aux or (overrides[inf][1] if inf in overrides and overrides[inf][1] else base_aux(inf))
    v = {"id": inf, "inf": inf, "en": en, "aux": a, "pp": pp, "prt": [prt[p] for p in PERSONS], "src": src}
    if not regular(inf, prt, pp):
        v["irr"] = True
    if context:
        v["ctx"] = context
    verbs[inf] = v


def main_verb(phrase):
    """The verb of a phrase: 'den Tisch decken' -> decken, 'sich die Zähne putzen' -> putzen."""
    phrase = re.sub(r"\(.*?\)", "", phrase).replace("|", "").strip()
    parts = [p.strip() for p in phrase.split("/")]
    out = []
    for p in parts:
        toks = [t for t in p.split() if t not in ("sich", "an", "mit", "zu", "+", "Dativ")]
        for t in reversed(toks):
            if t in DICT or t in overrides or t in common:
                out.append(t)
                break
    return out


# 1) Collins
for l in json.load(open(ASSETS / "lessons.json", encoding="utf-8")):
    for c in l["cards"]:
        if c["en"].startswith("to "):
            vs = main_verb(c["de"])
            single = len(vs) == 1 and re.sub(r"^sich ", "", c["de"].strip()) == vs[0]
            for v in vs:
                add(v, c["en"], "c", context="" if single else f"{c['de']} – {c['en']}")

# 2) Class: verb cards look like "vor|lesen (er liest vor, hat vorgelesen)" or "interessieren"
for l in json.load(open(ASSETS / "my_lessons.json", encoding="utf-8")):
    for c in l["cards"]:
        if not c["back"].startswith("to "):
            continue
        front = c["front"]
        aux = None
        m = re.search(r"\b(ist|hat)\s+(?:sich\s+)?\S+\)", front)
        if m:
            aux = "s" if m.group(1) == "ist" else "h"
        vs = main_verb(front)
        clean = re.sub(r"\(.*?\)", "", front).replace("|", "").strip()
        for v in vs:
            add(v, c["back"], "k", aux, context="" if clean in (v, "sich " + v) else f"{clean} – {c['back']}")

# overrides that no source mentions are still worth having when a Collins phrase uses them
# 3) frequent verbs
for inf, (aux, en) in common.items():
    if " " in inf:
        continue
    add(inf, en, "f", aux)

out = sorted(verbs.values(), key=lambda v: v["inf"])
json.dump(out, open(ASSETS / "verbs.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print(f"{len(out)} verbs, {sum(1 for v in out if v.get('irr'))} irregular;",
      {s: sum(1 for v in out if s in v['src']) for s in 'ckf'})
if missing:
    print("MISSING (add to tools/verbs/overrides.tsv):")
    for m in missing:
        print("  ", *m, sep="\t")
