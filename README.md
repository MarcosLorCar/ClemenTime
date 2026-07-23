# ClemenTime

ClemenTime is a schedule and tasks planner app for Android.

## Features

* Schedule management and timetables
* Matter and subject tracking
* Material 3 UI with dark and light mode support
* Localization support
* Data import and export for backups

## Building Locally

You can compile the app using the Gradle Wrapper:

```bash
./gradlew assembleDebug
```

The generated APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

## CI/CD

* CI: Runs tests and compiles the debug app on PRs and pushes to main.
* Release: Triggered by tags starting with v (e.g., v1.0.0). Builds the release APK and uploads it to GitHub Releases.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

