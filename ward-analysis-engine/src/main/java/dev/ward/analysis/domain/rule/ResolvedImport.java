package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.Symbol;
import java.util.Objects;
import java.util.Optional;

/**
 * Where an imported name leads inside the project.
 *
 * @param path project-root-relative path of the file the import resolves to
 * @param declaration the symbol that file declares under the imported name, or empty when the
 *     import names the module itself, or the file declares no such symbol
 */
public record ResolvedImport(String path, Optional<Symbol> declaration) {

  /**
   * Validates the fields.
   *
   * @throws NullPointerException if a field is missing
   */
  public ResolvedImport {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(declaration, "declaration");
  }
}
