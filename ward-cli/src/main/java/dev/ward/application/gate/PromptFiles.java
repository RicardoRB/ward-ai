package dev.ward.application.gate;

import dev.ward.domain.secrets.PromptInput;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The files a prompt attaches or references, as project-relative paths. A reference is an
 * {@code @path} in the message; one that names no file is dropped when the files are read, and a
 * path outside the project is never read.
 */
final class PromptFiles {
  private static final Pattern REFERENCE =
      Pattern.compile("(?<![\\w@])@([\\w./~+-][\\w./~+-]*)");

  private PromptFiles() {}

  /** Returns the distinct project-relative paths of {@code input}, in order, with {@code /}. */
  static List<String> relative(final Path root, final PromptInput input) {
    final Path base = root.toAbsolutePath().normalize();
    final Set<String> paths = new LinkedHashSet<>();
    input.files().forEach(file -> add(paths, base, file));
    final Matcher matcher = REFERENCE.matcher(input.message());
    while (matcher.find()) {
      add(paths, base, matcher.group(1));
    }
    return List.copyOf(paths);
  }

  private static void add(final Set<String> paths, final Path base, final String file) {
    try {
      final Path resolved = base.resolve(file).normalize();
      if (resolved.startsWith(base) && !resolved.equals(base)) {
        paths.add(base.relativize(resolved).toString().replace('\\', '/'));
      }
    } catch (InvalidPathException ignored) {
      // A name that is not a path cannot be a file of the project.
    }
  }
}
