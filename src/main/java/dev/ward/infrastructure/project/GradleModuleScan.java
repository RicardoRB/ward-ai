package dev.ward.infrastructure.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tells which Gradle modules init configures and which it leaves out, for the notes that name the
 * skipped ones. Module discovery itself stays in {@link GradleKotlinModules}.
 */
public final class GradleModuleScan {
  private GradleModuleScan() {}

  /** Returns the root-relative paths of the modules that have a build script but are not placed. */
  public static List<String> skipped(final Path root) {
    return ProjectDirectories.below(root).stream()
        .filter(dir -> !dir.equals(root))
        .filter(dir -> !GradleKotlinModules.nested(root, dir))
        .filter(dir -> GradleKotlinModules.scriptFile(dir).isPresent())
        .filter(dir -> !placed(dir))
        .map(dir -> root.relativize(dir).toString())
        .toList();
  }

  /** Returns whether a module script applies the Kotlin JVM plugin, or Java with Java sources. */
  static boolean placed(final Path dir) {
    final String script = GradleKotlinModules.scriptText(dir);
    return GradleKotlinModules.appliesKotlinJvm(script)
        || (GradleKotlinModules.appliesJava(script) && hasJavaSources(dir));
  }

  private static boolean hasJavaSources(final Path dir) {
    return Files.isDirectory(dir.resolve("src/main/java"))
        || Files.isDirectory(dir.resolve("src/test/java"));
  }
}
