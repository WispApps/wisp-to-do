# F-Droid packaging status

## Version 0.1.1 (versionCode 2)

- Release builds enable R8 and resource shrinking with optimized default rules.
- The project keep rules are explicitly passed to R8.
- Production release signing is not configured in this source archive; F-Droid signs its own builds.
- The separate `r8Test` build inherits the release optimization settings and uses the local debug key. Its application ID is `com.wisp.todo.r8test`, so it can be installed alongside the production app. Do not publish this test APK.
- The metadata template enables `AutoUpdateMode: Version` and `UpdateCheckMode: Tags`.
- Replace the template commit placeholder with the full commit hash of the tested, published `v0.1.1` tag. The template is not the live file in the F-Droid fork.
- Source inspection found explicit JSON keys in backup serialization; those keys do not depend on obfuscated model field names. Runtime backup compatibility still needs device testing.
- See `ARTWORK.md` for the recorded icon provenance.

## Verification

The release build was attempted in the editing environment but could not download the pinned Gradle distribution because networking is unavailable. Android SDK is also not installed here. Compilation, R8 execution, and device behavior are not verified by this archive.

Follow `docs/release-0.1.1.md` to build, test, and update the existing F-Droid merge request.
