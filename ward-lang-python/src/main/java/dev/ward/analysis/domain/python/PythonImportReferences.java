package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the imports of a Python file. An {@code import a.b} is {@code abs:a.b}; a {@code from}
 * import is {@code from:<level>:<module>:<name>}, with {@code *} for a star import. Imports count
 * wherever they appear, so the ones in functions, {@code try} blocks and {@code if TYPE_CHECKING:}
 * blocks are included.
 */
final class PythonImportReferences {

  private static final String IMPORT_NAME = "import_name";
  private static final String IMPORT_FROM = "import_from";
  private static final String ABSOLUTE = "abs:";
  private static final String FROM = "from:";
  private static final String SEPARATOR = ":";
  private static final String STAR = "*";
  private static final String DOT = ".";
  private static final String ELLIPSIS = "...";
  private static final int DOT_LEVELS = 1;
  private static final int ELLIPSIS_LEVELS = 3;

  private PythonImportReferences() {}

  /** The references of every import statement under {@code tree}, in source order. */
  static List<Reference> of(final SyntaxNode tree) {
    final List<Reference> references = new ArrayList<>();
    collect(tree, references);
    return references;
  }

  private static void collect(final SyntaxNode node, final List<Reference> references) {
    if (IMPORT_NAME.equals(node.kind())) {
      importName(node, references);
    } else if (IMPORT_FROM.equals(node.kind())) {
      importFrom(node, references);
    } else {
      node.children().forEach(child -> collect(child, references));
    }
  }

  private static void importName(final SyntaxNode node, final List<Reference> references) {
    PythonSyntax.children(node, "dotted_as_names").stream()
        .flatMap(names -> PythonSyntax.children(names, "dotted_as_name").stream())
        .flatMap(alias -> PythonSyntax.children(alias, "dotted_name").stream())
        .forEach(
            name ->
                references.add(
                    new Reference(ABSOLUTE + PythonSyntax.dottedName(name), node.span())));
  }

  private static void importFrom(final SyntaxNode node, final List<Reference> references) {
    final String prefix = FROM + level(node) + SEPARATOR + module(node) + SEPARATOR;
    for (final String name : importedNames(node)) {
      references.add(new Reference(prefix + name, node.span()));
    }
  }

  /** The relative level: one per dot, three per ellipsis token, zero for an absolute import. */
  private static int level(final SyntaxNode node) {
    int level = 0;
    for (final SyntaxNode child : node.children()) {
      if (DOT.equals(PythonSyntax.text(child))) {
        level += DOT_LEVELS;
      } else if (ELLIPSIS.equals(PythonSyntax.text(child))) {
        level += ELLIPSIS_LEVELS;
      }
    }
    return level;
  }

  private static String module(final SyntaxNode node) {
    return PythonSyntax.children(node, "dotted_name").stream()
        .findFirst()
        .map(PythonSyntax::dottedName)
        .orElse("");
  }

  /** The imported names, or {@code *} for a star import. */
  private static List<String> importedNames(final SyntaxNode node) {
    final boolean star =
        node.children().stream().anyMatch(child -> STAR.equals(PythonSyntax.text(child)));
    if (star) {
      return List.of(STAR);
    }
    return PythonSyntax.children(node, "import_as_names").stream()
        .flatMap(names -> PythonSyntax.children(names, "import_as_name").stream())
        // The first name of an alias is the imported one; a name after 'as' is the local alias.
        .flatMap(alias -> PythonSyntax.children(alias, "name").stream().limit(1))
        .map(PythonSyntax::dottedName)
        .toList();
  }
}
