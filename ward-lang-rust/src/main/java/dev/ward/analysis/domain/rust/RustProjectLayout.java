package dev.ward.analysis.domain.rust;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The crates of a Cargo project, which map a source file to its module and a crate name to its
 * root. A file that is not a module of a library or binary root, such as an example or a test, has
 * no module.
 *
 * @param crates the crates of the project
 */
public record RustProjectLayout(List<RustCrate> crates) {
  private static final String SUFFIX = ".rs";
  private static final String SEPARATOR = "/";
  private static final String BIN = "bin";
  private static final List<String> ROOT_FILES = List.of("lib", "main");
  private static final String MODULE_FILE = "mod";

  /** Copies the crates. */
  public RustProjectLayout {
    crates = List.copyOf(crates);
  }

  /** Returns the crate named {@code name} in a path, or empty when the project has none. */
  public Optional<RustCrate> crate(final String name) {
    return crates.stream().filter(crate -> crate.name().equals(name)).findFirst();
  }

  /** Returns the module the file at {@code path} defines, or empty when it defines none. */
  public Optional<RustModule> moduleOf(final String path) {
    return crates.stream()
        .filter(crate -> path.startsWith(prefix(crate)))
        .max(Comparator.comparingInt(crate -> crate.sourceDirectory().length()))
        .flatMap(crate -> moduleIn(crate, path.substring(prefix(crate).length())));
  }

  private static Optional<RustModule> moduleIn(final RustCrate crate, final String relative) {
    if (!relative.endsWith(SUFFIX)) {
      return Optional.empty();
    }
    final String[] parts = relative.substring(0, relative.length() - SUFFIX.length()).split(
        SEPARATOR
    );
    if (BIN.equals(parts[0]) && parts.length > 1) {
      return Optional.empty();
    }
    final String last = parts[parts.length - 1];
    final int keep = isRootFile(parts) || MODULE_FILE.equals(
        last
    ) ? parts.length - 1 : parts.length;
    return Optional.of(new RustModule(crate.name(), List.of(parts).subList(0, keep)));
  }

  private static boolean isRootFile(final String... parts) {
    return parts.length == 1 && ROOT_FILES.contains(parts[0]);
  }

  private static String prefix(final RustCrate crate) {
    return crate.sourceDirectory().isEmpty() ? "" : crate.sourceDirectory() + SEPARATOR;
  }
}
