package dev.ward.infrastructure.init.gradle;

import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the Gradle build scripts init edits, and restores them when a write fails. */
final class GradleScriptFiles {
  private GradleScriptFiles() {}

  /**
   * Writes every edit that changed. When a write fails, the scripts already written in this call
   * are restored before the error is raised, so a failed apply never leaves a half-configured
   * build.
   */
  static void writeAll(final List<GradleBuildFileEdit> edits) {
    final List<GradleBuildFileEdit> written = new ArrayList<>();
    try {
      for (final GradleBuildFileEdit edit : edits) {
        if (!edit.tools().isEmpty()) {
          write(edit.buildFile(), edit.content());
          written.add(edit);
        }
      }
    } catch (WardException failure) {
      for (final GradleBuildFileEdit edit : written.reversed()) {
        try {
          restore(edit.buildFile(), edit.original());
        } catch (WardException restoreFailure) {
          failure.addSuppressed(restoreFailure);
        }
      }
      throw failure;
    }
  }

  /** Writes {@code content} over {@code file}, the content init read it with. */
  static void restore(final Path file, final String content) {
    try {
      write(file, content);
    } catch (WardException e) {
      throw new WardException("Could not restore " + file + ": " + e.getMessage(), e);
    }
  }

  /** The root build script: the Kotlin DSL first, then the Groovy one. */
  static Path buildFile(final Path root) {
    final Path kotlin = root.resolve("build.gradle.kts");
    final Path groovy = root.resolve("build.gradle");
    if (Files.isRegularFile(kotlin)) {
      return kotlin;
    }
    if (Files.isRegularFile(groovy)) {
      return groovy;
    }
    throw new WardException(
        "Could not configure Gradle tools: no build.gradle or build.gradle.kts found.");
  }

  private static void write(final Path file, final String content) {
    try {
      Files.writeString(
          file, content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new WardException(
          "Could not configure Gradle tools in " + file + ": " + e.getMessage(), e);
    }
  }
}
