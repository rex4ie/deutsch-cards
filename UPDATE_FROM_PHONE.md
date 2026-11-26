# Updating Class and Finds from your phone

Two ways, from best to fallback. In both, your notes only live in the chat. They are never
saved in the repo; only the generated cards are.

---

## Option A: Claude Code in the Claude app (recommended)

Works fully from the phone: Claude edits the deck file and pushes it, and the app picks it
up next time you open the tab. No app update, no APK build.

1. Open the Claude app → **Code**.
2. Start a session on the repository **rex4ie/deutsch-cards** (branch `main`).
3. Send one of these messages, with your notes pasted underneath:

   **Class lesson**
   ```
   Update my lessons with today's class (follow UPDATE_FROM_PHONE.md).
   Commit only app/src/main/assets/my_lessons.json (and verbs.json) and push to main.

   <paste your class notes here>
   ```

   **Finds (video, song, podcast, research)**
   ```
   Update my finds (follow UPDATE_FROM_PHONE.md).
   Source: <song / video / where you found it>
   Commit only app/src/main/assets/my_finds.json and push to main.

   <paste the words, lines or notes here>
   ```
4. When it says it pushed, open the **Class** or **Finds** tab in the app (or tap refresh).

Tips:
- A photo of your notebook or a screenshot works too. Attach it instead of pasting text.
- Several days at once is fine: "These are three lessons: Monday, Tuesday, Wednesday…".
- To fix a card: "In my lessons, the card 'ich kann' should say 'I can / I am able to'".

---

## Option B: plain Claude chat (fallback)

Use this when Claude Code isn't available. Claude writes the cards, and you paste them
into GitHub yourself.

1. Start a normal chat and **attach this file**.
2. Write: *"Make Class cards from these notes"* (or *"Finds cards"*), then paste your notes.
3. Claude replies with one JSON lesson object.
4. On GitHub (browser or GitHub app), open
   `app/src/main/assets/my_lessons.json` (Class) or `my_finds.json` (Finds) → edit (pencil).
5. Before the final `]`, add a comma after the last `}`, paste the new lesson, and commit
   to `main`.

---

## Rules for Claude (both options)

You are adding cards to a German flashcard app. There are two decks that must **never be
mixed**:

| Deck | Use for | File |
|---|---|---|
| Class | the owner's German classes | `app/src/main/assets/my_lessons.json` |
| Finds | videos, songs, podcasts, own research | `app/src/main/assets/my_finds.json` |

If it's unclear which deck the notes belong to, ask. A word from a song goes in Finds even
if it also came up in class.

### Format

Each file is a JSON array of lessons:

```json
[
  {
    "id": "2026-11-20",
    "title": "Modal verbs",
    "date": "20 Nov 2026",
    "topic": "grammar",
    "cards": [
      { "front": "ich kann", "back": "I can", "note": "können is irregular in the singular" },
      { "front": "der Tisch", "back": "table" }
    ]
  }
]
```

- `id`: unique and permanent. Use the date (`YYYY-MM-DD`); if that id already exists, add
  a suffix (`2026-11-20-b`). For Finds, a short slug of the source also works
  (`sonne-rammstein`).
- `title`: Class → the lesson's subject. Finds → the source, e.g. `"Sonne – Rammstein"`.
- `topic`: one short tag. Class: `grammar`, `vocab`, `phrases`, … Finds: `song`, `video`,
  `podcast`, `research`.
- `front`: what's shown first (usually German). `back`: the answer (usually English).
  `note` (optional): rule, exception, or for Finds the line where the word appeared.
- Plain text only, no markdown.

### Turning notes into good cards

- **Nouns**: singular, with the article on the front: `"der Tisch"` → `"table"`. Cards that
  are exactly *article + noun* also feed the app's der/die/das game. Put the plural in
  `note` if the notes give it (`"Plural: die Tische"`).
- **Verbs**: infinitive on the front, back starting with "to …"; irregular forms in `note`
  (`"gehen"` → `"to go"`, note `"ging, ist gegangen"`).
  **Class verbs also go into the verb-forms game, automatically**, even when the notes only
  give the present tense. With Claude Code (Option A), follow "Verb forms" in `CLAUDE.md`
  to rebuild `app/src/main/assets/verbs.json` and commit it too.
- **Grammar**: one fact per card, phrased as a question:
  `"Perfekt of gehen"` → `"ist gegangen"`; `"Dative of 'der'"` → `"dem"`.
  Never paste a whole paragraph onto a card.
- **Phrases**: whole phrase on the front, translation on the back.
- **Finds**: put the original line in `note`, e.g. `"Hier kommt die Sonne"`.
- Fix obvious typos in the notes, and use proper umlauts (ä ö ü ß).
- Skip anything that isn't worth memorising (dates, homework reminders, page numbers).

### Never lose progress

The app keys each card's review schedule on `<lessonId>` plus the card's position. So:

1. Read the current file first and **extend** it. Never rewrite it from scratch.
2. Never change an existing lesson's `id`.
3. Never reorder or delete cards inside an existing lesson. New cards for an existing lesson
   go **at the end** of its `cards` array.
4. New lessons go at the **end** of the array.
5. To correct a card, edit its `front`, `back` or `note` in place, keeping its position.

### Before pushing (Option A)

- Check that the file is valid JSON.
- Commit **only** the one deck file (plus `verbs.json` for a Class update with verbs). Message, for example: `Class: add 2026-11-20 Modal verbs`.
- Push to `main`. No version bump, no app build: the app downloads the file itself.
- Reply with a short summary: deck, lesson title, number of cards added.
