# ClemenTime

ClemenTime is a schedules and tasks planner app for Android, built with Jetpack Compose. It allows you to import, view, and manage your timetables and tasks on a clean, modern interface.

## Download

You can download the latest APK from the **[Releases](https://github.com/MarcosLorCar/ClemenTime/releases/latest)** page.

## Features

- **Timetable Management**: View and coordinate daily and weekly schedules.
- **Adaptive Layout**: Responsive UI supporting phones, tablets, and foldables.
- **Material 3 UI**: Clean look with support for light and dark themes, plus dynamic color styling.
- **Home Screen Widget**: A reactive widget showing current and upcoming events at a glance.
- **Local Cache & Updates**: Download schedule files from online repositories with automatic update checking.
- **Conflict Resolution**: Highlight and resolve overlapping schedule slots upon import.
- **Data Portability**: Backup and restore data using import/export features.
- **Privacy First**: Fully offline operation with no account registration or analytics tracking.

## Development

You can build the app locally using the Gradle Wrapper.

### Prerequisites

- Android SDK (API 34+)
- JDK 17 or higher

### Building the APK

Run the following command in your terminal:

```bash
./gradlew assembleDebug
```

The compiled debug APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
