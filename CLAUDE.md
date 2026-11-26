# Deutsch Cards

Kotlin + Jetpack Compose Android app for learning German: a Duolingo-style lesson path
(Collins German 3000 words), an Anki-style review deck, and a separate "Mine" deck built
from the owner's own class notes.

## Building and shipping

- There is no local Android toolchain. **GitHub Actions is the compiler**: every push to
  `main` builds a signed APK and publishes it to the `latest` release. The app's About page
  installs it (self-updater). Check a run with `gh run watch <id>`.
- Pushes that only touch `my_lessons.json`, `my_finds.json` or `*.md` skip the APK build
  (`paths-ignore` in `.github/workflows/build-apk.yml`).
- Signing secrets: `SIGNING_KEYSTORE_B64`, `SIGNING_PASSWORD` (already set on the repo).
- `versionCode` = GitHub run number. Never hand-edit it.

## "Update my lessons" workflow

(For sessions started from the phone or the cloud, where `My Lessons/` doesn't exist, the
notes are pasted into the chat instead; follow `UPDATE_FROM_PHONE.md`.)

The owner pastes daily class notes into `My Lessons/notes/` (any format: word lists,
grammar rules, sentences, teacher's notes). That folder is **gitignored and must never be
committed or pushed**: the notes are private.

When asked to update the lessons:

1. Read every file in `My Lessons/notes/`.
2. Regenerate `app/src/main/assets/my_lessons.json`, a JSON array of lessons:
   ```json
   [
     {
       "id": "2026-11-20",
       "title": "Modal verbs",
       "date": "20 Nov 2026",
       "topic": "grammar",
       "cards": [
         { "front": "ich kann", "back": "I can", "note": "können: irregular in singular" }
       ]
     }
   ]
   ```
   - `front` is the prompt (usually German), `back` the answer, `note` optional extra
     (rule, example, exception). Plain text, no markdown.
   - Grammar: turn rules into question/answer cards (e.g. front "Perfekt of gehen",
     back "ist gegangen"), not paragraphs.
   - Nouns: put the article on the front, singular, e.g. front "der Tisch", back "table".
     Cards whose front is exactly "der/die/das + Noun" also feed the der/die/das (Artikel) game.
   - One lesson per note file / class day. `topic` is a short tag (grammar, vocab, …).
3. **Keep progress stable.** Card ids are `my:<lessonId>:<cardIndex>` and the app's review
   schedule is keyed on them. So:
   - Never change an existing lesson's `id`.
   - Never reorder or delete existing cards inside a lesson; only append new ones at the end.
   - New lessons go at the end of the array.
   Read the current `my_lessons.json` first and extend it rather than rewriting from scratch.
4. **Verbs go into the verb-forms game too, automatically** (the owner doesn't write past
   forms in the notes; work them out). Write verb cards as the infinitive on the front
   (`vor|lesen (er liest vor, hat vorgelesen)` style is fine), back starting with "to …".
   Then rebuild `verbs.json` (see "Verb forms" below) and check its output: every new class
   verb must be listed, none under MISSING. Add a missing or wrong verb to
   `tools/verbs/overrides.tsv` (with its Präteritum and Partizip II) and rebuild.
5. Validate the JSON (e.g. `python -c "import json;json.load(open(...,encoding='utf-8'))"`).
6. Commit and push **only** `app/src/main/assets/my_lessons.json` and
   `app/src/main/assets/verbs.json`. Confirm with `git status` that nothing under
   `My Lessons/` is staged.
7. No app update is needed: the Class tab downloads the file from
   `raw.githubusercontent.com` when opened (or via its refresh button), and the verb game
   downloads `verbs.json` the same way.

## "Update my finds" workflow

A second, completely separate personal deck: words from videos, songs, podcasts and the
owner's own research. Same rules as above, with these differences:

| | Class ("update my lessons") | Finds ("update my finds") |
|---|---|---|
| Notes (local only, gitignored) | `My Lessons/notes/` | `My Finds/notes/` |
| Output (pushed) | `app/src/main/assets/my_lessons.json` | `app/src/main/assets/my_finds.json` |
| Card ids | `my:<lessonId>:<i>` | `find:<lessonId>:<i>` |
| App tab | Class | Finds |

Never mix the two: a word from a song goes in Finds even if it also came up in class.
For a song or video, use the source as the lesson `title` (e.g. "Sonne – Rammstein") and
`topic` "song", "video", "podcast" or "research"; put the line it appeared in into `note`.

## Verb forms (Practice tab → Verb forms)

`app/src/main/assets/verbs.json` holds the verbs for the Präteritum / Perfekt game: Collins
verbs, Class verbs and ~570 frequent verbs (`tools/verbs/common.tsv`: infinitive, `h`/`s`
for hat/ist, English). Forms come from the `german-verbs-dict` npm package (LanguageTool
data, CC-BY-SA 4.0; the app credits it). Rebuild after changing any source:

```
cd <scratch dir> && npm pack german-verbs-dict && tar xzf german-verbs-dict-*.tgz
python tools/build_verbs.py <scratch dir>/package/dist/verbs.json
```

The script prints counts and a MISSING list; fix those in `tools/verbs/overrides.tsv`,
which also overrides wrong dictionary forms (e.g. inseparable über-/durch- verbs). Progress
is keyed on the infinitive, so rebuilding never loses it. A push that only changes
`verbs.json` needs no APK build; the app fetches it.

## Other data

- Collins vocabulary: `tools/extract.py` turns the PDF (local only, in `Resrources/`,
  gitignored and copyrighted) into `app/src/main/assets/lessons.json`. Audio file names
  are stable per card, so re-extracting must not change card order.
- Audio: `tools/gen_audio.py` (edge-tts, `de-DE-KatjaNeural`) then `tools/compress_audio.py`
  (Opus 20 kbps `.webm`).
- Example sentences: `tools/examples/*.tsv` (`id<TAB>German<TAB>English`), merged by
  `tools/build_examples.py` into `assets/examples.json`.

## Design

"Schwarzwald" theme: forest green (`C.Accent`) on warm cream, serif headings, rounded soft
cards, with a dark variant that follows the system setting. All colors come from `C` in
`ui/Theme.kt`. Red means mistakes/due only. Use Material icons, never emoji. The owner
dislikes generic "AI-looking" UI.
