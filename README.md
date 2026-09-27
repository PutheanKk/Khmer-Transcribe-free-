# Khmer Subtitle AI (Android + Gemini API)

App Android សាមញ្ញមួយ ដែល:
1. ឲ្យអ្នកប្រើជ្រើសរើសវីដេអូពីទូរស័ព្ទ
2. Upload វីដេអូទៅ Gemini File API
3. ស្នើឲ្យ Gemini ស្តាប់សំឡេង **ស្គាល់ភាសាដើមស្វ័យប្រវត្តិ** (auto language detection)
4. **បកប្រែជាភាសាខ្មែរ** ហើយបង្កើត Subtitle ជាទម្រង់ **SRT** ដែលមាន timestamp
5. អាចមើលលទ្ធផលក្នុង app និងចែករំលែក/រក្សាទុកជាឯកសារ `.srt`

## តម្រូវការ
- Android Studio (ជំនាន់ថ្មីៗ, Koala ឬក្រោយ)
- គណនី Google AI Studio → បង្កើត **Gemini API Key** ដោយឥតគិតថ្លៃនៅ
  https://aistudio.google.com/app/apikey

## របៀបដំឡើង
1. បើក folder នេះក្នុង Android Studio (Open an existing project)
2. បង្កើតឯកសារ `local.properties` (ប្រសិនបើមិនទាន់មាន) ហើយបន្ថែមបន្ទាត់:
   ```
   GEMINI_API_KEY=សូមដាក់_API_KEY_របស់អ្នកនៅទីនេះ
   ```
3. Sync Gradle ហើយចុច Run លើ device/emulator (Android 8.0+ / API 26 ឡើងទៅ)

## រចនាសម្ព័ន្ធកូដ
- `GeminiApiService.kt` — ហៅ Gemini File API (upload) + generateContent (transcribe + translate + SRT)
- `SubtitleUtils.kt` — រក្សាទុកលទ្ធផលជា `.srt`
- `MainActivity.kt` — UI (Jetpack Compose): ជ្រើសវីដេអូ → បង្ហាញ progress → បង្ហាញ subtitle → share

## ចំណុចសំខាន់ៗគួរដឹង
- **វីដេអូធំ / វែង**: Gemini File API អាចទទួលឯកសារធំ តែពេលវេលា upload + processing អាចយូរ។ សម្រាប់វីដេអូវែងជាង ~10 នាទី គួរពិចារណា split ជាដុំៗ ដើម្បីជៀសវាង timeout។
- **Model name**: កូដប្រើ `gemini-2.5-flash` — ប្រសិនបើ Google ប្តូរឈ្មោះ model សូមកែក្នុង `GeminiApiService.kt` (`MODEL_NAME`)។
- **សុវត្ថិភាព API Key**: កុំ hardcode key ក្នុង code ដែលនឹង push ទៅ public repo។ Key ត្រូវបានទាញចេញពី `local.properties` តាមរយៈ `BuildConfig.GEMINI_API_KEY`។ សម្រាប់ app ដែលនឹង release ជាសាធារណៈ គួរហៅ Gemini API តាមរយៈ backend server ផ្ទាល់ខ្លួន មិនត្រូវដាក់ key ក្នុង APK ដោយផ្ទាល់ទេ។
- **Format SRT**: Gemini ត្រូវបានស្នើឲ្យឆ្លើយតបជា JSON (`{detected_language, srt}`) ដើម្បីងាយ parse ជាភាសា Kotlin។

## ជំហានបន្ថែមដែលអាចធ្វើ (optional)
- បន្ថែម `MediaCodec`/`ExoPlayer` ដើម្បីលេងវីដេអូ + subtitle ដំណាលគ្នាក្នុង app
- បន្ថែម progress % ពិតប្រាកដពេល upload (OkHttp progress listener)
- Cache លទ្ធផលដើម្បីជៀសវាងហៅ API ដដែលៗ
