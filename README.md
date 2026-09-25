# Wisp To Do

Android application for projects, lists, tasks, favorites, scheduled reminders, encrypted archive import/export, and encrypted Nextcloud backup.

## Open and build

1. Open this folder in Android Studio.
2. Let Gradle download the declared dependencies.
3. Connect an Android device with USB debugging enabled, or choose an emulator.
4. In Windows PowerShell, build with `.\gradlew.bat assembleDebug`, then install with `.\gradlew.bat installDebug`.

The repository contains source code and resources. Android Studio creates local build output itself. When initializing Git from the Android Studio project folder, commit `gradlew`, `gradlew.bat` and `gradle/wrapper/` as well. `local.properties`, signing keys and local credentials are intentionally ignored by Git.

## Moving to Wisp To Do

Extract this project into a new, empty folder rather than over an existing checkout. The application ID is `com.wisp.todo`, so Android installs it as a separate application. Before removing the previous app, export its data and keep its recovery phrase. Import that archive into Wisp To Do using the same phrase.

The `.atodo.enc` extension and encrypted archive signatures remain unchanged for compatibility with previous exports. Nextcloud backups now use the `WispToDo` folder; files in the previous remote folder are not moved automatically.

The Gradle wrapper is included and pinned to Gradle 9.6.0 with its SHA-256 checksum. The app compiles Java and Kotlin to JVM 17 bytecode and requires Android SDK 35. Use JDK 17 or newer; the build does not force a vendor-specific JDK download.

## Encrypted archives

Export creates an `.atodo.enc` file encrypted with AES-256-GCM and the recovery phrase. Import merges projects, lists and tasks from such a file. Keep both the archive and the phrase: neither one replaces the other.

## Project links

- [GitLab — WispApps](https://gitlab.com/wispapps)
- [GitHub — WispApps](https://github.com/WispApps)
- [GNU licenses](https://www.gnu.org/licenses/)

## License

Copyright © 2026 WispApps contributors.

Wisp To Do is licensed under the GNU General Public License, version 3 or (at your option) any later version (`GPL-3.0-or-later`). The complete license text is included in [LICENSE](LICENSE). The official license is available from the GNU Project at [gnu.org/licenses](https://www.gnu.org/licenses/) and the [GPL version 3 page](https://www.gnu.org/licenses/gpl-3.0.html).

## F-Droid

F-Droid listing text is in `fastlane/metadata/android/`. See [F-DROID-STATUS.md](F-DROID-STATUS.md) for the packaging checklist and remaining review items.

## Development notes

Use an Android 26+ device. Notification permission is requested before a reminder receives a date and time on Android 13+, and can also be requested from Settings.
