# Deutsch Cards

Kotlin + Jetpack Compose Android app for learning German: a Duolingo-style lesson path
(Collins German 3000 words), an Anki-style review deck, and a separate "Mine" deck built
from the owner's own class notes.

## Building and shipping

- There is no local Android toolchain. **GitHub Actions is the compiler**: every push to
  `main` builds a signed APK and publishes it to the `latest` release. The app's About page
  installs it (self-updater). Check a run with `gh run watch <id>`.
- Pushes that only touch `app/src/main/assets/my_lessons.json` or `*.md` skip the APK build
  (`paths-ignore` in `.github/workflows/build-apk.yml`).
- Signing secrets: `SIGNING_KEYSTORE_B64`, `SIGNING_PASSWORD` (already set on the repo).
- `versionCode` = GitHub run number. Never hand-edit it.

## "Update my lessons" workflow

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
4. Validate the JSON (e.g. `python -c "import json;json.load(open(...,encoding='utf-8'))"`).
5. Commit and push **only** `app/src/main/assets/my_lessons.json`. Confirm with
   `git status` that nothing under `My Lessons/` is staged.
6. No app update is needed: the Mine tab downloads the file from
   `raw.githubusercontent.com` when opened (or via its refresh button).

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
