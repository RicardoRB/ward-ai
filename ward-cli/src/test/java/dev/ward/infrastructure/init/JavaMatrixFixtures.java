package dev.ward.infrastructure.init;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Copies the fixtures of the Java compatibility matrix and seeds their violations. */
final class JavaMatrixFixtures {
  static final Path VIOLATIONS = Path.of("src/test/resources/java-matrix/violations");
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/java-matrix");
  private static final String POM = "pom.xml";
  private static final String MAVEN = "maven";
  private static final int RECORDS = 17;
  private static final int PATTERN_SWITCH = 21;

  private JavaMatrixFixtures() {}

  /**
   * Copies the Java 8 sources, each newer syntax layer the JDK supports, and the build files. A
   * named fixture brings its own sources and build files over the shared build tool files.
   */
  static void copyFixture(
      final Path project, final int jdk, final String build, final String fixture)
      throws IOException {
    if (fixture.isEmpty()) {
      JavaMatrixProcesses.copyTree(FIXTURES.resolve("sources/base"), project);
      if (jdk >= RECORDS) {
        JavaMatrixProcesses.copyTree(FIXTURES.resolve("sources/java17"), project);
      }
      if (jdk >= PATTERN_SWITCH) {
        JavaMatrixProcesses.copyTree(FIXTURES.resolve("sources/java21"), project);
      }
    } else {
      JavaMatrixProcesses.copyTree(FIXTURES.resolve(fixture).resolve("sources"), project);
    }
    JavaMatrixProcesses.copyTree(FIXTURES.resolve(build), project);
    if (!fixture.isEmpty()) {
      JavaMatrixProcesses.copyTree(FIXTURES.resolve(fixture).resolve(build), project);
    }
    try (Stream<Path> files = Files.list(project)) {
      for (final Path file : files.filter(Files::isRegularFile).toList()) {
        final String content = Files.readString(file);
        if (content.contains("@RELEASE@")) {
          Files.writeString(file, content.replace("@RELEASE@", String.valueOf(jdk)));
        }
      }
    }
  }

  /** The seeded compile error: a Java one, or the named fixture's own, in its source language. */
  static String compileError(final String fixture) {
    return fixture.isEmpty() ? "compile-error" : fixture + "-compile-error";
  }

  /** The seeded coverage violation: an untested class, in the fixture's source language. */
  static String coverageViolation(final String fixture) {
    return fixture.isEmpty() ? "coverage" : fixture + "-coverage";
  }

  /**
   * Adds the common violations, and for Maven a dynamic dependency version for {@code build-policy}
   * and a version conflict for Enforcer.
   */
  static void seedCommon(final Path project, final String buildTool) throws IOException {
    JavaMatrixProcesses.copyTree(VIOLATIONS.resolve("common"), project);
    if (MAVEN.equals(buildTool)) {
      final Path pom = project.resolve(POM);
      final String dependency =
          Files.readString(VIOLATIONS.resolve("maven/dependency.xml"))
              + Files.readString(VIOLATIONS.resolve("maven/convergence.xml"));
      Files.writeString(
          pom, Files.readString(pom).replaceFirst("(?s)(\\s*</dependencies>)", dependency + "$1"));
    } else {
      seedGradle(project);
    }
  }

  /** A dynamic dependency version in the Gradle build script, for {@code build-policy}. */
  private static void seedGradle(final Path project) throws IOException {
    final Path kotlin = project.resolve("build.gradle.kts");
    final Path script = Files.exists(kotlin) ? kotlin : project.resolve("build.gradle");
    final String dependency =
        Files.exists(kotlin)
            ? "implementation(\"junit:junit:4.+\")"
            : "implementation 'junit:junit:4.+'";
    Files.writeString(
        script, Files.readString(script) + "\ndependencies {\n    " + dependency + "\n}\n");
  }
}
