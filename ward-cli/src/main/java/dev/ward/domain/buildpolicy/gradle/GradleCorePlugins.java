package dev.ward.domain.buildpolicy.gradle;

import java.util.Set;

/** The plugins that ship with Gradle and need no version. */
public final class GradleCorePlugins {
  private static final Set<String> IDS =
      Set.of(
          "java",
          "java-library",
          "java-platform",
          "java-gradle-plugin",
          "java-test-fixtures",
          "application",
          "groovy",
          "scala",
          "war",
          "ear",
          "jacoco",
          "maven-publish",
          "ivy-publish",
          "publishing",
          "signing",
          "checkstyle",
          "pmd",
          "codenarc",
          "distribution",
          "base",
          "idea",
          "eclipse",
          "version-catalog",
          "jvm-test-suite",
          "antlr",
          "visual-studio",
          "xcode",
          "cpp-application",
          "cpp-library",
          "swift-application",
          "swift-library",
          "build-dashboard",
          "project-report-plugin",
          "reporting-base",
          "lifecycle-base",
          "wrapper");

  private GradleCorePlugins() {}

  /** Returns whether {@code id} names a plugin that ships with Gradle. */
  public static boolean contains(final String id) {
    return IDS.contains(id);
  }
}
