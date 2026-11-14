# Android projects

Read this when the project applies the Android Gradle Plugin. Init detects it from the root or a
module build script (`com.android.application` or `com.android.library`, by plugin id or catalog
alias). The build tool stays Gradle, so the Gradle wrapper, JDK and module rules still apply.

## Inspection

- Each Android module is listed with its script. Modules applying no JVM plugin get only the Android
  gate blocks, never the JVM quality gates.
- Note the Android SDK: `ANDROID_HOME` or `ANDROID_SDK_ROOT` should name an existing directory.
  Init does not install it; doctor warns when it is missing.

## Choices

- **Gates.** `android-lint`, `android-unit-test` and `android-coverage` (80% minimum). Standard and
  strict both select them. The JVM `coverage` gate is not offered on an Android project.
- **Blocks.** `android-coverage` writes `enableUnitTestCoverage = true` into the debug build type of
  each Android module script. Nothing else is written into Android module scripts.

- **Native rules.** No init option. The `ward` gate reads `AndroidManifest.xml` and the XML files
  under `res/` and runs the `ward:android/*` rules of the `standard` and `strict` presets (exported
  components without permission, debuggable, cleartext traffic, `allowBackup`, hard-coded text,
  missing content descriptions). `android-lint` stays for checks that need the platform API.

## Blocking findings

- `ward:android/*` findings of the `ward` gate, with their file, line and column in the manifest or
  resource file. Fix the file; `tools:ignore` is not honored.
- The Gradle checks that apply to every Gradle project.

## Known limits

- Java and Kotlin sources in an Android module do not get the `ward` gate in this version; it stays
  in the root, which applies no Java plugin.
- `unused-resources` is not native yet.
- Instrumented tests, PIT mutation testing and Kotlin Multiplatform are not covered.
