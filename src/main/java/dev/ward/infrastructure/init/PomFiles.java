package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Writes the poms of one init run, and restores them when a write fails. */
final class PomFiles {
  private PomFiles() {}

  /**
   * Writes the aggregator and then each module pom that changed. When a write fails, the poms
   * already written in this call are restored before the error is raised.
   */
  static void writeAll(final PomEdit root) {
    final List<PomEdit> written = new ArrayList<>();
    try {
      for (final PomEdit edit : root.files()) {
        if (!edit.tools.isEmpty()) {
          write(edit.pom, edit.content);
          written.add(edit);
        }
      }
    } catch (WardException failure) {
      for (final PomEdit edit : written.reversed()) {
        try {
          write(edit.pom, edit.original);
        } catch (WardException restoreFailure) {
          failure.addSuppressed(restoreFailure);
        }
      }
      throw failure;
    }
  }

  /** Writes {@code content} over {@code pom}, failing with the file named. */
  static void write(final Path pom, final String content) {
    try {
      Files.writeString(
          pom, content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new WardException(
          "Could not configure Maven tools in " + pom + ": " + e.getMessage(), e);
    }
  }
}
