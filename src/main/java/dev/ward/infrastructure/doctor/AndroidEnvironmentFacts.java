package dev.ward.infrastructure.doctor;

import dev.ward.infrastructure.project.AndroidGradlePluginVersion;
import dev.ward.infrastructure.project.GradleWrapperVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Reads the Android SDK and build-tool facts that the doctor's Android findings check. */
final class AndroidEnvironmentFacts {
  private AndroidEnvironmentFacts() {}

  /** Records whether each SDK variable names a directory, and the AGP and Gradle versions. */
  static Map<String, String> read(final Path projectRoot) {
    return Map.of(
        "androidSdkHome",
        Boolean.toString(isSdkDirectory("ANDROID_HOME")),
        "androidSdkRoot",
        Boolean.toString(isSdkDirectory("ANDROID_SDK_ROOT")),
        "androidGradlePluginVersion",
        AndroidGradlePluginVersion.read(projectRoot).orElse(""),
        "gradleVersion",
        GradleWrapperVersion.read(projectRoot).orElse(""));
  }

  /** Whether the variable names an existing directory; its path never leaves this class. */
  private static boolean isSdkDirectory(final String name) {
    final String value = System.getenv(name);
    return value != null && !value.isBlank() && Files.isDirectory(Path.of(value));
  }
}
