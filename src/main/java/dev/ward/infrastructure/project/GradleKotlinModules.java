package dev.ward.infrastructure.project;

import dev.ward.domain.project.Language;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds the Gradle modules whose own build script applies a JVM plugin (Kotlin, or Java with Java
 * sources), so init can configure each module where its code is. A convention plugin hides the
 * plugin, so its module is not found and the build stays in the root script.
 */
public final class GradleKotlinModules {
  private static final String BUILD_SRC = "buildSrc";
  private static final List<String> SETTINGS = List.of("settings.gradle", "settings.gradle.kts");
  private static final List<String> BUILDS = List.of("build.gradle.kts", "build.gradle");
  private static final Pattern KOTLIN_JVM =
      Pattern.compile(
          "org\\.jetbrains\\.kotlin\\.jvm|kotlin\\(\\s*\"jvm\"\\s*\\)|alias\\s*\\([^)]*kotlin[.-]jvm");
  private static final Pattern JAVA =
      Pattern.compile("[`'\"](java|java-library)[`'\"]|apply\\s+plugin:\\s*['\"]java['\"]");

  private GradleKotlinModules() {}

  /**
   * Returns the module directories below {@code root} that init configures: a build script that
   * applies the Kotlin JVM plugin, or the Java plugin with Java sources. Directories in {@code
   * buildSrc}, in a nested settings build or in build output are skipped.
   */
  public static List<Path> of(final Path root) {
    return ProjectDirectories.below(root).stream()
        .filter(dir -> !dir.equals(root))
        .filter(dir -> !nested(root, dir))
        .filter(GradleModuleScan::placed)
        .toList();
  }

  /** Returns whether {@code root} has Kotlin sources in its project or its modules. */
  public static boolean hasKotlinSources(final Path root) {
    return JvmSourceLanguages.of(root).contains(Language.KOTLIN);
  }

  /** Returns whether the build script text applies the Kotlin JVM plugin by id or catalog alias. */
  public static boolean appliesKotlinJvm(final String script) {
    return KOTLIN_JVM.matcher(script).find();
  }

  /** Returns whether the build script text applies the Java or Java library plugin. */
  public static boolean appliesJava(final String script) {
    return JAVA.matcher(script).find();
  }

  /** Returns the build script file of {@code dir}, the Kotlin DSL first, when there is one. */
  public static Optional<Path> scriptFile(final Path dir) {
    return BUILDS.stream().map(dir::resolve).filter(Files::isRegularFile).findFirst();
  }

  /** Returns the text of the build script of {@code dir}, or none when it has no script. */
  static String scriptText(final Path dir) {
    return scriptFile(dir).map(GradleKotlinModules::read).orElse("");
  }

  /** A directory below {@code root} that is buildSrc, or sits under its own settings file. */
  static boolean nested(final Path root, final Path dir) {
    for (Path current = dir;
        current != null && !current.equals(root);
        current = current.getParent()) {
      if (BUILD_SRC.equals(String.valueOf(current.getFileName())) || hasSettings(current)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasSettings(final Path dir) {
    return SETTINGS.stream().anyMatch(name -> Files.isRegularFile(dir.resolve(name)));
  }

  private static String read(final Path script) {
    try {
      return Files.readString(script);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read build script " + script, e);
    }
  }
}
