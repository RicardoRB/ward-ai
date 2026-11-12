package dev.ward.infrastructure.buildpolicy;

import dev.ward.domain.buildpolicy.BuildFileException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Helpers every reader shares: project-relative names and file text. */
public final class BuildPaths {
  private BuildPaths() {}

  /** Returns {@code name} inside the project-relative {@code dir}, with {@code /}. */
  public static String relative(final String dir, final String name) {
    return dir.isEmpty() ? name : dir + "/" + name;
  }

  /** Returns the file {@code name} inside {@code dir} of {@code root}. */
  public static Path file(final Path root, final String dir, final String name) {
    return root.resolve(dir).resolve(name);
  }

  /** Returns whether any of {@code names} is a regular file in {@code dir}. */
  public static boolean anyFile(final Path root, final String dir, final List<String> names) {
    return names.stream().anyMatch(name -> Files.isRegularFile(file(root, dir, name)));
  }

  /**
   * Returns the text of {@code path}, named {@code shown} in errors.
   *
   * @throws BuildFileException if the file cannot be read
   */
  public static String read(final Path path, final String shown) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (final IOException exception) {
      throw new BuildFileException(shown, "the file could not be read", exception);
    }
  }

  /** Returns the 1-based line that holds the character at {@code index} of {@code text}. */
  public static int lineAt(final String text, final int index) {
    return (int) text.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
  }

  /** Returns the 1-based line of the first line of {@code text} that contains {@code needle}. */
  public static int lineOf(final String text, final String needle) {
    final int index = text.indexOf(needle);
    if (index < 0) {
      return 0;
    }
    return (int) text.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
  }
}
