# Release 0.1.1: R8 and F-Droid updates

## Apply the source archive

Extract the archive into the existing Wisp To Do project root, where `settings.gradle.kts` resides. The archive has no enclosing directory and contains no `.git`, signing keys, local SDK paths, or build output. Review `git diff` after extraction. Commit and publish only after testing.

## Build and test on Windows

Open PowerShell in the project root. Use Android Studio's JBR as JAVA_HOME if needed (adjust the installation path if different):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleRelease :app:installR8Test
```

Connect an Android phone with USB debugging enabled. `installR8Test` installs **Wisp To Do R8 Test**, a separate application with the same release R8/resource-shrinking configuration. It is signed by your local debug key. It does not update or share storage with the existing app. The production release APK remains unsigned unless signing is configured separately; do not try to install the unsigned APK.

Test the optimized copy:

- Launch, select language/theme, create projects/lists/tasks, edit and delete items.
- Export a backup from the existing app; use its recovery phrase in the test app and import the backup. Verify titles, notes, favorites, completion status and groups.
- Export/import from the optimized app itself.
- Schedule a reminder and verify delivery. Verify pending reminders after restarting the phone.
- If using Nextcloud, test against a separate test account or directory to avoid replacing a production cloud backup.

Check `app/build/outputs/mapping/release/mapping.txt` and `app/build/outputs/mapping/r8Test/mapping.txt` after successful builds. Preserve the release mapping file outside the source repository for crash analysis.

A passing build does not by itself prove that all optimized app features work. If a build fails, retain the first relevant R8/Gradle error. Do not hide errors using broad `-dontwarn` or disable optimization.

## Commit and tag the tested source

Version values in this archive are `versionName = "0.1.1"` and `versionCode = 2`. Use a new `v0.1.1` tag. Do not move the existing `v1.0.0` tag. Review the diff and stage only intended files, then commit. With remotes named `gitlab` and `github`:

```powershell
git diff --check
git status --short
git add -- app/build.gradle.kts app/src/main/keepRules/rules.keep app/src/r8Test/AndroidManifest.xml docs/fdroiddata-com.wisp.todo.yml.template docs/release-0.1.1.md F-DROID-STATUS.md fastlane/metadata/android/en-US/changelogs/2.txt fastlane/metadata/android/ru/changelogs/2.txt
git commit -m "Enable R8 and prepare F-Droid auto-updates"
git tag -a v0.1.1 -m "Wisp To Do 0.1.1"
git push gitlab main
git push github main
git push gitlab v0.1.1
git push github v0.1.1
```

Run each command after the previous command succeeds. If a push is rejected, reconcile the branches; do not force-push. Publish only a production APK signed using your established release key, never the R8 Test APK. No release keystore is included in this archive.

## Update the existing F-Droid MR

Generate a complete YAML file from the committed release tag:

```powershell
$releaseSha = git rev-parse "v0.1.1^{commit}"
if ($LASTEXITCODE -ne 0 -or $releaseSha -notmatch '^[0-9a-f]{40}$') { throw "Release tag not found" }
$template = [System.IO.File]::ReadAllText((Resolve-Path .\docs\fdroiddata-com.wisp.todo.yml.template))
$metadata = $template.Replace("REPLACE_WITH_RELEASE_COMMIT_SHA", $releaseSha.Trim())
$output = Join-Path $env:TEMP "com.wisp.todo.yml"
[System.IO.File]::WriteAllText($output, $metadata.Replace("`r`n", "`n"), [System.Text.UTF8Encoding]::new($false))
notepad $output
```

The tag must point to the tested source committed and pushed above. In the existing **wispapps/f-droid-data** MR branch, replace `metadata/com.wisp.todo.yml` with the generated contents. Keep the final newline. This replaces the old build entry with version 0.1.1 / code 2, the new full commit hash, and tag-based auto-update settings. Do not copy the placeholder template unchanged into the fork.

Wait for the new pipeline and inspect Reports again. Once verified, reply in the existing MR with the new source commit and state that R8 and tag-based auto-update are enabled. Keep reproducible-build and ABI-split checkboxes unchecked unless separately implemented.
