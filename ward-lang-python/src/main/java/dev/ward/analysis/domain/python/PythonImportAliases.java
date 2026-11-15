package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the names that the {@code import} statements of a file bind to what they stand for: {@code
 * import subprocess as sp} binds {@code sp} to {@code subprocess}, and {@code from os import
 * system} binds {@code system} to {@code os.system}. Relative imports are left out, because the
 * module they name is not a library name.
 */
final class PythonImportAliases {

  private static final String DOTTED_NAME = "dotted_name";
  private static final String IMPORT = "import";
  private static final String DOT = ".";

  private PythonImportAliases() {}

  /** Reads every import statement under {@code tree}, whatever scope it is in. */
  static Map<String, String> of(final SyntaxNode tree) {
    final Map<String, String> aliases = new HashMap<>();
    collect(tree, aliases);
    return Map.copyOf(aliases);
  }

  private static void collect(final SyntaxNode node, final Map<String, String> aliases) {
    switch (node.kind()) {
      case "import_name" -> importName(node, aliases);
      case "import_from" -> importFrom(node, aliases);
      default -> node.children().forEach(child -> collect(child, aliases));
    }
  }

  private static void importName(final SyntaxNode statement, final Map<String, String> aliases) {
    for (final SyntaxNode names : PythonSyntax.children(statement, "dotted_as_names")) {
      for (final SyntaxNode alias : PythonSyntax.children(names, "dotted_as_name")) {
        final String module =
            PythonSyntax.dottedName(PythonSyntax.children(alias, DOTTED_NAME).get(0));
        final List<SyntaxNode> bound = PythonSyntax.children(alias, PythonNames.NAME);
        if (bound.isEmpty()) {
          aliases.put(root(module), root(module));
        } else {
          aliases.put(PythonNames.identifier(bound.get(0)), module);
        }
      }
    }
  }

  private static void importFrom(final SyntaxNode statement, final Map<String, String> aliases) {
    final String module = fromModule(statement);
    if (module.isEmpty() || module.startsWith(DOT)) {
      return;
    }
    for (final SyntaxNode names : PythonSyntax.children(statement, "import_as_names")) {
      for (final SyntaxNode alias : PythonSyntax.children(names, "import_as_name")) {
        final List<SyntaxNode> parts = PythonSyntax.children(alias, PythonNames.NAME);
        final String member = PythonNames.identifier(parts.get(0));
        aliases.put(PythonNames.identifier(parts.get(parts.size() - 1)), module + DOT + member);
      }
    }
  }

  private static String fromModule(final SyntaxNode statement) {
    final StringBuilder module = new StringBuilder();
    for (final SyntaxNode child : statement.children()) {
      final String text = PythonSyntax.text(child);
      if (text.startsWith(DOT)) {
        module.append(text);
      } else if (DOTTED_NAME.equals(child.kind())) {
        module.append(PythonSyntax.dottedName(child));
      } else if (IMPORT.equals(text)) {
        break;
      }
    }
    return module.toString();
  }

  private static String root(final String dotted) {
    final int dot = dotted.indexOf(DOT);
    return dot < 0 ? dotted : dotted.substring(0, dot);
  }
}
