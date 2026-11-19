# Deutsch Cards

An Android (Kotlin + Jetpack Compose) app for learning the **Collins German 3000 words and phrases**.

- **Learn**: a Duolingo-style path. Each page of the PDF is one lesson. Lessons mix new-word cards, multiple choice, listening, matching pairs and typing games. Every word has German audio made with `edge-tts`.
- **Review**: an Anki-style spaced-repetition deck (SM-2: Again / Hard / Good / Easy). It shows how much of the word bank you've covered, and you can review due cards or practise any lesson you've already done.

## Data pipeline
```
python tools/extract.py Resrources/Collins_German_3000_words_and_phrases.pdf app/src/main/assets/lessons.json
python tools/gen_audio.py app/src/main/assets/lessons.json app/src/main/assets/audio
```

## APK
GitHub Actions builds the APK on every push to `main`. Download it from the **latest** release, or from the workflow run's artifacts.
