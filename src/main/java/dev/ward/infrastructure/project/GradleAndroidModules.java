package dev.ward.infrastructure.project;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds the Gradle modules whose own build script applies the Android Gradle Plugin, by plugin id
 * or by a version-catalog alias, so init can place the Android gates in each module. Android
 * modules apply no Kotlin JVM or Java plugin, so {@link GradleKotlinModules} does not find them.
 */
public final class GradleAndroidModules {
  private static final Pattern ANDROID =
      Pattern.compile(
          "com\\.android\\.(application|library)|alias\\s*\\([^)]*android[.-](application|library)");

  private GradleAndroidModules() {}

  /**
   * Returns the module directories below {@code root} whose build script applies the Android Gradle
   * Plugin. {@code root} itself, {@code buildSrc}, nested settings builds and build output are
   * skipped, as for {@link GradleKotlinModules#of}.
   */
  public static List<Path> of(final Path root) {
    return ProjectDirectories.below(root).stream()
        .filter(dir -> !dir.equals(root))
        .filter(dir -> !GradleKotlinModules.nested(root, dir))
        .filter(dir -> appliesAndroid(GradleKotlinModules.scriptText(dir)))
        .toList();
  }

  /** Returns whether the build script text applies the Android Gradle Plugin. */
  public static boolean appliesAndroid(final String script) {
    return ANDROID.matcher(script).find();
  }
}
