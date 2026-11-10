package dev.ward.infrastructure.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes and reads the files of a formatting test project. */
final class FormatTestFiles {
  private FormatTestFiles() {}

  /** Writes a UTF-8 file under the project, creating its folders. */
  static void write(final Path project, final String relative, final String text)
      throws IOException {
    final Path file = project.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text, StandardCharsets.UTF_8);
  }

  /** Reads a UTF-8 file under the project. */
  static String read(final Path project, final String relative) throws IOException {
    return Files.readString(project.resolve(relative), StandardCharsets.UTF_8);
  }
}
