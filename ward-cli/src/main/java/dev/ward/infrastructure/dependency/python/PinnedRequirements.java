package dev.ward.infrastructure.dependency.python;

import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.infrastructure.project.python.PyprojectDependencies;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads pinned {@code requirements*.txt} files, following {@code -r} and {@code -c} includes inside
 * the project. A line that names a package is pinned only with {@code ==} or {@code ===} and an
 * exact version; anything else is an error naming the file and the line.
 */
final class PinnedRequirements {
  private static final Pattern PIN =
      Pattern.compile(
          "^([A-Za-z0-9][A-Za-z0-9._-]*)\\s*(?:\\[[^\\]]*\\])?\\s*(?:===|==)\\s*([^\\s,;*]+)$");
  private static final Pattern HASH = Pattern.compile("\\s--hash[= ]\\S+");
  private static final Pattern INCLUDE =
      Pattern.compile("^(?:-r|--requirement|-c|--constraint)\\s*=?\\s*(\\S+)$");
  private static final String VIA = "via";

  private final Path root;
  private final List<Pin> pins = new ArrayList<>();
  private final Set<Path> visited = new HashSet<>();

  /** A pinned requirement. */
  record Pin(String name, String version, String file, boolean transitive) {}

  private PinnedRequirements(final Path root) {
    this.root = root;
  }

  /**
   * Reads {@code file} and what it includes.
   *
   * @throws LockfileFormatException on an unpinned, editable, URL or VCS requirement, or an include
   *     that is missing or outside the project
   */
  static List<Pin> read(final Path root, final Path file) {
    final PinnedRequirements reader = new PinnedRequirements(root);
    reader.file(file);
    return reader.pins;
  }

  private void file(final Path file) {
    final Path normal = file.toAbsolutePath().normalize();
    if (!visited.add(normal)) {
      return;
    }
    final String label = root.relativize(normal).toString().replace('\\', '/');
    final List<String> lines;
    try {
      lines = Files.readAllLines(normal);
    } catch (IOException e) {
      throw new LockfileFormatException(label + " cannot be read", e);
    }
    Optional<Pin> last = Optional.empty();
    boolean inVia = false;
    final List<String> via = new ArrayList<>();
    for (final Logical logical : logicalLines(lines)) {
      if (logical.text().startsWith("#")) {
        inVia = annotation(logical.text().substring(1).strip(), inVia, via);
        continue;
      }
      flush(last, via);
      via.clear();
      last =
          logical.text().isEmpty()
              ? Optional.empty()
              : requirement(normal, label, logical.number(), logical.text(), via);
      inVia = last.isPresent() && (!via.isEmpty() || logical.text().contains("# via"));
    }
    flush(last, via);
  }

  /** A line with its backslash continuations joined, and the number of its first physical line. */
  private record Logical(int number, String text) {}

  private static List<Logical> logicalLines(final List<String> lines) {
    final List<Logical> result = new ArrayList<>();
    final StringBuilder joined = new StringBuilder();
    int first = -1;
    for (int index = 0; index < lines.size(); index++) {
      final String raw = lines.get(index);
      final boolean continued = raw.endsWith("\\");
      joined.append(continued ? raw.substring(0, raw.length() - 1) : raw).append(' ');
      first = first < 0 ? index + 1 : first;
      if (!continued) {
        result.add(new Logical(first, joined.toString().strip()));
        joined.setLength(0);
        first = -1;
      }
    }
    return result;
  }

  /** Records a pip-compile {@code # via} annotation; returns whether more items may follow. */
  private static boolean annotation(
      final String text, final boolean inVia, final List<String> via) {
    if (text.startsWith(VIA)) {
      final String rest = text.substring(VIA.length()).strip();
      if (!rest.isEmpty()) {
        via.add(rest);
      }
      return true;
    }
    if (inVia && !text.isEmpty()) {
      via.add(text);
      return true;
    }
    return inVia;
  }

  private void flush(final Optional<Pin> last, final List<String> via) {
    final boolean transitive =
        !via.isEmpty()
            && via.stream().noneMatch(item -> item.startsWith("-r") || item.startsWith("-c"));
    last.ifPresent(
        pin ->
            pins.set(
                pins.lastIndexOf(pin), new Pin(pin.name(), pin.version(), pin.file(), transitive)));
  }

  /** Reads one logical line; returns the pin it adds, or none for an option or an include. */
  private Optional<Pin> requirement(
      final Path file,
      final String label,
      final int number,
      final String line,
      final List<String> via) {
    final String inline = comment(line, via);
    final String text = HASH.matcher(inline).replaceAll("").strip();
    if (text.isEmpty()) {
      return Optional.empty();
    }
    if (text.startsWith("-")) {
      option(file, label, number, text);
      return Optional.empty();
    }
    final int marker = text.indexOf(';');
    final String requirement = (marker < 0 ? text : text.substring(0, marker)).strip();
    final Matcher pin = PIN.matcher(requirement);
    if (!pin.matches()) {
      throw new LockfileFormatException(
          label
              + " line "
              + number
              + ": '"
              + requirement
              + "' is not an exact version (use name==version)");
    }
    final Pin result =
        new Pin(PyprojectDependencies.normalize(pin.group(1)), pin.group(2), label, false);
    pins.add(result);
    return Optional.of(result);
  }

  /** Splits off a trailing comment, adding a {@code # via} annotation to {@code via}. */
  private static String comment(final String line, final List<String> via) {
    final int hash = line.indexOf(" #");
    if (hash < 0) {
      return line;
    }
    final String note = line.substring(hash + 2).strip();
    if (note.startsWith(VIA)) {
      annotation(note, false, via);
    }
    return line.substring(0, hash);
  }

  private void option(final Path file, final String label, final int number, final String text) {
    if (text.startsWith("-e") || text.startsWith("--editable")) {
      throw new LockfileFormatException(
          label + " line " + number + ": an editable requirement is not an exact version");
    }
    final Matcher include = INCLUDE.matcher(text);
    if (include.matches()) {
      final Path target = include(file, label, number, include.group(1));
      if (text.startsWith("-r") || text.startsWith("--requirement")) {
        file(target);
      }
    }
  }

  private Path include(final Path file, final String label, final int number, final String name) {
    final Path target = file.getParent().resolve(name).normalize();
    if (!target.startsWith(root) || !Files.isRegularFile(target)) {
      throw new LockfileFormatException(
          label + " line " + number + ": the included file " + name + " is not in the project");
    }
    return target;
  }
}
