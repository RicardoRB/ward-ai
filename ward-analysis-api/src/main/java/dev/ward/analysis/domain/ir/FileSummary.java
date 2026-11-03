package dev.ward.analysis.domain.ir;

import java.util.List;
import java.util.Objects;

/**
 * What one source file exports, imports and declares, computed from that file alone. Lists are
 * copied on construction and returned as immutable views.
 *
 * @param path project-root-relative path with {@code /} separators
 * @param exports the symbols other files can see
 * @param imports the references to other files
 * @param declared every symbol the file declares, exported or not
 */
public record FileSummary(
    String path, List<Symbol> exports, List<Reference> imports, List<Symbol> declared) {

  /**
   * Copies the lists.
   *
   * @throws NullPointerException if a field is missing or a list holds a missing element
   */
  public FileSummary {
    Objects.requireNonNull(path, "path");
    exports = List.copyOf(exports);
    imports = List.copyOf(imports);
    declared = List.copyOf(declared);
  }
}
