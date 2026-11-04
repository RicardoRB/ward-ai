package dev.ward.analysis.domain.swift;

import java.util.List;
import java.util.Optional;

/**
 * The modules of a Swift project. A file in no target belongs to the implicit module, which no
 * import can name.
 *
 * @param targets the modules the project declares
 */
public record SwiftProjectLayout(List<SwiftTarget> targets) {

  /** The module of every file that no target owns. */
  public static final String IMPLICIT_MODULE = "(implicit)";

  private static final String SEPARATOR = "/";

  /**
   * Copies the targets.
   *
   * @throws NullPointerException if the targets are missing
   */
  public SwiftProjectLayout {
    targets = List.copyOf(targets);
  }

  /**
   * Returns the module that owns {@code path}: a target that lists the file, else the target whose
   * directory holds it most deeply, else the implicit module.
   */
  public String moduleOf(final String path) {
    final Optional<String> listed =
        targets.stream()
            .filter(target -> target.files().contains(path))
            .map(SwiftTarget::name)
            .findFirst();
    return listed.orElseGet(() -> byDirectory(path));
  }

  /** Tells whether the project declares a module with this name. */
  public boolean declares(final String module) {
    return targets.stream().anyMatch(target -> target.name().equals(module));
  }

  private String byDirectory(final String path) {
    String owner = IMPLICIT_MODULE;
    int deepest = -1;
    for (final SwiftTarget target : targets) {
      for (final String directory : target.directories()) {
        if (path.startsWith(directory + SEPARATOR) && directory.length() > deepest) {
          owner = target.name();
          deepest = directory.length();
        }
      }
    }
    return owner;
  }
}
