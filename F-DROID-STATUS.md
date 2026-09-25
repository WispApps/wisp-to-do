# F-Droid packaging status

## Prepared

- App source is marked `GPL-3.0-or-later`; the full GPL text is in `LICENSE`.
- English and Russian F-Droid listing text and a version 1 changelog are in `fastlane/metadata/android/`.
- Android application ID is `com.wisp.todo`; minimum SDK is 26 and compile/target SDK is 35.
- Java and Kotlin compile targets are JVM 17. The Gradle daemon no longer forces a JDK 25 download.
- Gradle 9.6.0 is pinned with a wrapper distribution SHA-256.
- Dependencies are version-pinned; no local JAR/AAR/native library files or NDK build scripts are included. Gradle repositories are Google Maven and Maven Central (plus the Gradle plugin marker repository).
- Static dependency scan found no Firebase, Play Services, ad, or analytics SDK references. The app uses Internet for user-configured Nextcloud WebDAV backups.

## Before submitting to the main F-Droid repository

1. Publish the complete source in a public Git repository and create a release tag matching the version in `app/build.gradle.kts`. The GitLab repository path in the template assumes the repository is named `wisp-to-do`; update it if the actual repository path differs.
2. Verify and document the redistribution license for the Wisp icon artwork. The icon was supplied as an image; its AI model, weights license, and output terms are not recorded in this source archive. F-Droid requires lawful, redistributable licenses for non-code assets. Do not claim the artwork is GPL-licensed until the rights and license are confirmed.
3. Copy `docs/fdroiddata-com.wisp.todo.yml.template` into the separate `fdroiddata/metadata/com.wisp.todo.yml` only after the public repository and `v0.1.0` tag exist. Adjust the `Repo` and `commit` fields to the real source location/tag.
4. Run `fdroid readmeta`, `fdroid lint com.wisp.todo`, and `fdroid build com.wisp.todo` in an F-Droid build environment. This workspace has no Android SDK, so an isolated release build has not been verified here.
5. Provide at least one current app screenshot or feature graphic if you want the app to qualify for F-Droid's Latest tab.

F-Droid maintainers make the final inclusion decision. Besides licensing and buildability, they assess the app's practical value and privacy characteristics. Wisp To Do's user-controlled encrypted Nextcloud backup is the distinctive privacy feature to describe accurately; it is manual backup/restore, not continuous bidirectional sync.
