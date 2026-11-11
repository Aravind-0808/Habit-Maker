# Contributing to Habit Maker

Thanks for helping. Bug reports, ideas and pull requests are all welcome.

## Reporting bugs and asking for features

Open an [issue](../../issues/new/choose) using one of the templates. For a bug, include:

- your phone model and Android version
- the steps to reproduce it
- what you expected and what happened
- logs (`adb logcat`) if the app crashed

## Development setup

1. Fork the repository and clone your fork.
2. Open it in Android Studio, or build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```
3. Create a branch for your change: `git checkout -b fix/reminder-after-reboot`.

## Before you open a pull request

Run these three commands and make sure they pass:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

CI runs the same checks on every pull request.

## Code guidelines

- **Kotlin + Jetpack Compose only.** Don't add XML layouts.
- **Follow the package layout:** UI lives in `feature/<area>`, logic that doesn't need Android in `core/`, and storage in `data/`.
- **Put decisions in plain functions you can unit-test**, like `BlockPolicy` and `Streaks`. Keep services and receivers thin.
- **Changing the database:** bump the version in `AppDatabase`, add a `Migration`, and commit the new schema JSON from `app/schemas/`. Never break existing users' data.
- **Privacy:** don't add analytics, ads, trackers or network calls without discussing it in an issue first. The app is local-only on purpose.
- **Only contribute code and assets you wrote yourself or that have a compatible open-source license.** Never copy them from other apps.
- Keep pull requests focused: one fix or feature per PR.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add weekly habit goals
fix: reschedule reminders after time-zone change
docs: explain the accessibility permission
```

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
