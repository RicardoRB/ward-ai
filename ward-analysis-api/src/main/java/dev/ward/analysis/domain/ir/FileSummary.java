package dev.ward.analysis.domain.ir;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What one source file exports, imports, declares and uses, computed from that file alone. The
 * collections are copied on construction and returned as immutable views.
 *
 * @param path project-root-relative path with {@code /} separators
 * @param exports the symbols other files can see
 * @param imports the references to other files
 * @param declared every symbol the file declares, exported or not
 * @param usedNames the identifiers the file refers to, apart from the names of its own
 *     declarations; empty for a language whose rules do not need them
 */
public record FileSummary(
    String path,
    List<Symbol> exports,
    List<Reference> imports,
    List<Symbol> declared,
    Set<String> usedNames) {

  /**
   * Copies the collections.
   *
   * @throws NullPointerException if a field is missing or a collection holds a missing element
   */
  public FileSummary {
    Objects.requireNonNull(path, "path");
    exports = List.copyOf(exports);
    imports = List.copyOf(imports);
    declared = List.copyOf(declared);
    usedNames = Set.copyOf(usedNames);
  }

  /** Creates a summary that records no used names. */
  public FileSummary(
      final String path,
      final List<Symbol> exports,
      final List<Reference> imports,
      final List<Symbol> declared) {
    this(path, exports, imports, declared, Set.of());
  }
}
