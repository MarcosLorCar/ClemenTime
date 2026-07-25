# ClemenTime

ClemenTime is a schedules and tasks planner app for Android, built with Jetpack Compose. It allows you to import, view, and manage your timetables and tasks on a clean, modern interface.

## Features

- **Timetable Management**: View and coordinate daily and weekly schedules.
- **Adaptive Layout**: Responsive UI supporting phones, tablets, and foldables.
- **Material 3 UI**: Clean look with support for light and dark themes, plus dynamic color styling.
- **Home Screen Widget**: A reactive widget showing current and upcoming events at a glance.
- **Local Cache**: Download schedule files from online repositories for offline use.
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

Copyright (C) 2026 Marcos Loro Carrasco

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.

See the [LICENSE](LICENSE) file for the full text.
