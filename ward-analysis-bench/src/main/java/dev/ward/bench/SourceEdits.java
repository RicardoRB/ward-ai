package dev.ward.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Picks the source file that edit-latency runs touch, and appends to it. */
final class SourceEdits {

  private static final Set<String> IGNORED_SEGMENTS =
      Set.of(".git", ".ward", "target", "build", "node_modules");
  private static final Map<String, Set<String>> SOURCE_EXTENSIONS =
      Map.of(
          "java", Set.of(".java"),
          "node", Set.of(".ts", ".js"),
          "python", Set.of(".py"),
          "swift", Set.of(".swift"));

  private SourceEdits() {}

  /** The first source file of the entry's language in sorted order, outside build output. */
  static Path target(final Path copy, final String language) {
    final Set<String> extensions = SOURCE_EXTENSIONS.getOrDefault(language, Set.of());
    try (Stream<Path> files = Files.walk(copy)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> !isIgnored(copy.relativize(file)))
          .filter(
              file ->
                  extensions.stream().anyMatch(extension -> file.toString().endsWith(extension)))
          .min(Comparator.comparing(file -> copy.relativize(file).toString()))
          .orElseThrow(() -> new IllegalStateException("No source file for language " + language));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not list the corpus working copy", e);
    }
  }

  /** Appends one newline; the file is never restored, so each edit is a real change. */
  static void appendNewline(final Path file) {
    try {
      Files.writeString(file, "\n", StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not edit the source file", e);
    }
  }

  private static boolean isIgnored(final Path relative) {
    for (final Path segment : relative) {
      if (IGNORED_SEGMENTS.contains(segment.toString())) {
        return true;
      }
    }
    return false;
  }
}
