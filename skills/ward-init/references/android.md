# Android projects

Read this when the project applies the Android Gradle Plugin. Init detects it from the root or a
module build script (`com.android.application` or `com.android.library`, by plugin id or catalog
alias). The build tool stays Gradle, so the Gradle wrapper, JDK and module rules still apply.

## Inspection

- Each Android module is listed with its script. Modules applying no JVM plugin get only the Android
  gate blocks, never Checkstyle, PMD or SpotBugs.
- Note the Android SDK: `ANDROID_HOME` or `ANDROID_SDK_ROOT` should name an existing directory.
  Init does not install it; doctor warns when it is missing.

## Choices

- **Gates.** `android-lint`, `android-unit-test` and `android-coverage` (80% minimum). Standard and
  strict both select them. The JVM `coverage` gate is not offered on an Android project.
- **Blocks.** `android-coverage` writes `enableUnitTestCoverage = true` into the debug build type of
  each Android module script. Nothing else is written into Android module scripts.

## Blocking findings

- None beyond the Gradle checks that apply to every Gradle project.

## Known limits

- Java and Kotlin sources in an Android module do not get Checkstyle, PMD or SpotBugs in this
  version; those gates stay in the root, which applies no Java plugin.
- Instrumented tests, PIT mutation testing and Kotlin Multiplatform are not covered.
