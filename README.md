# Talkwise

**Learn English by talking.** Talkwise is an Android app where you practise spoken English with Wise, an AI conversation partner that finds your level and grows with you.

<!-- Add the Play Store badge and screenshots here once the listing is live. -->

## Features

- **Real conversation practice.** Speak or type, and Wise answers out loud, like a patient partner who always has time.
- **Adapts to your level.** Wise scores your vocabulary, grammar and fluency as you talk and moves you between Beginner, Intermediate and Advanced without a placement test.
- **Gentle corrections.** When a sentence could be better, you see the corrected version and a short tip, without breaking the flow.
- **Role-play scenarios.** Order at a café, sit a job interview, check in at the airport, meet a new neighbour, hold a friendly debate, or just talk freely.
- **Word pocket.** Save useful words with their meanings while you talk and review them later.
- **Session recap.** Each session ends with two things you did well and one thing to practise next.
- **Daily goal and streak.** Set a daily speaking goal and keep your streak alive, with a freeze for the day you miss.
- **No account needed.** Open the app and start talking.

## Requirements

- Android 8.0 (API 26) or newer
- An internet connection
- A microphone and a speech recognition service for voice mode; a keyboard mode is available on devices without one

## Privacy

- Talkwise signs you in anonymously. It does not ask for your name, email address or phone number.
- Your level, streak, daily progress and saved words are stored in Cloud Firestore under your anonymous ID, and only that ID can read or write them (see [`firestore.rules`](firestore.rules)).
- What you say is converted to text by the speech recognition service on your device. The text of the conversation is sent to Google's Gemini models through Firebase AI Logic to produce Wise's replies.
- The microphone is used only while you are in a conversation in voice mode.

## Tech stack

- Kotlin and Jetpack Compose, single activity, MVVM
- Gemini through Firebase AI Logic, with the model name configurable through Remote Config
- Android `SpeechRecognizer` and `TextToSpeech`
- Firebase Anonymous Authentication, Cloud Firestore and App Check
- A custom design system ("Nocturne") built on Compose Foundation, with an AGSL shader for the tutor's orb

## Building from source

The app needs a Firebase project to talk to. Without one it still builds, and shows a setup screen instead of the tutor.

1. Create a Firebase project and add an Android app with the package name `com.surajdev.talkwise`.
2. Download `google-services.json` into the `app/` folder. The file is git-ignored.
3. In the Firebase console, enable **Anonymous** sign-in under Authentication.
4. Create a Cloud Firestore database and publish the rules from [`firestore.rules`](firestore.rules).
5. Enable **AI Logic** with the Gemini Developer API.
6. Open the project in Android Studio and run the `app` configuration.

```
./gradlew :app:assembleDebug        # build the debug APK
./gradlew :app:testDebugUnitTest    # run the unit tests
```

### Configuration

| Setting | Where | Purpose |
| --- | --- | --- |
| `tutor_model` | Firebase Remote Config (string) | Gemini model used by the tutor. Falls back to the built-in default when unset. |
| `DAILY_TURNS` | `TalkViewModel` | Conversation turns each learner can take per day (60). |
| App Check | Firebase console | Release builds use Play Integrity. Debug builds use the debug provider, which prints its token to Logcat under `DebugAppCheckProvider`. |

## Project layout

```
app/src/main/java/com/surajdev/talkwise/
  ai/TutorService.kt        prompt, JSON schema and Gemini calls
  data/Progress.kt          level smoothing and streak rules
  data/Scenarios.kt         role-play scenarios
  data/UserRepository.kt    Firestore profile, words, sessions
  speech/                   speech recognition and text-to-speech
  ui/components/Orb.kt      the tutor's animated orb (AGSL shader, Canvas fallback)
  ui/talk/                  the conversation screen and its view model
  ui/home, welcome, recap, pocket
  ui/theme/                 Nocturne colours and type
```

## Credits

Fonts: [Sora](https://github.com/sora-xor/sora-font) and [Inter](https://github.com/rsms/inter), both under the SIL Open Font License.
