package dev.ward.analysis.domain.rule;

import java.util.List;
import java.util.Optional;

/**
 * The package or module name that an import reference names, read from the specifier that the
 * summarizer of its language wrote. A use of a name and a relative import name no package.
 */
final class ImportedName {
  // shortcut: the engine knows the spellings of the five language summarizers; move this behind
  // FileSummarizer when a sixth language or a second reader of the names appears.
  private static final List<String> PREFIXES =
      List.of(
          "import:",
          "on-demand:",
          "static:",
          "type:",
          "require:",
          "dynamic:",
          "abs:",
          "module:"
      );
  private static final String PYTHON_FROM = "from:0:";
  private static final String PYTHON_SEPARATOR = ":";
  private static final List<String> RELATIVE = List.of(".", "/");

  private ImportedName() {}

  /** Returns the name that {@code specifier} imports, or empty when it imports no package. */
  static Optional<String> of(final String specifier) {
    if (specifier.startsWith(PYTHON_FROM)) {
      return Optional.of(pythonFrom(specifier.substring(PYTHON_FROM.length())));
    }
    return PREFIXES.stream()
        .filter(specifier::startsWith)
        .findFirst()
        .map(prefix -> specifier.substring(prefix.length()))
        .filter(name -> !name.isEmpty() && RELATIVE.stream().noneMatch(name::startsWith));
  }

  /** {@code module:name} of an absolute {@code from module import name} is {@code module.name}. */
  private static String pythonFrom(final String rest) {
    final int separator = rest.indexOf(PYTHON_SEPARATOR);
    return separator < 0 ? rest : rest.substring(0, separator) + "." + rest.substring(
        separator + 1
    );
  }
}
