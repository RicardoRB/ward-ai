package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The named imports of one TypeScript or JavaScript file. A name imported from a module maps to
 * {@code module.name}, which is the receiver type that the flow sites report for it.
 *
 * <p>shortcut: only named imports are read, from the text of each import statement; default and
 * namespace imports have no receiver type. Upgrade with the module resolver when a rule needs them.
 */
final class NodeImports {

  private static final String IMPORT_STATEMENT = "importStatement";
  private static final Pattern NAMED =
      Pattern.compile("import\\s*(?:type\\s*)?\\{([^}]*)}\\s*from\\s*['\"]([^'\"]+)['\"]");

  private final Map<String, String> named;

  private NodeImports(final Map<String, String> named) {
    this.named = named;
  }

  /**
   * Reads the named imports of a file.
   *
   * @param root the root of the syntax tree of the file
   * @return the imports of the file
   */
  static NodeImports of(final SyntaxNode root) {
    final Map<String, String> named = new HashMap<>();
    collect(root, named);
    return new NodeImports(named);
  }

  /**
   * Returns the receiver type of a name imported from a module.
   *
   * @param local the name as the file uses it
   * @return the {@code module.name} of the import, or null when the name is not imported
   */
  String typeOf(final String local) {
    return named.get(local);
  }

  private static void collect(final SyntaxNode node, final Map<String, String> named) {
    if (IMPORT_STATEMENT.equals(node.kind())) {
      addNamed(NodeFlowSyntax.text(node).replaceAll("\\s+", " "), named);
    } else {
      node.children().forEach(child -> collect(child, named));
    }
  }

  private static void addNamed(final String text, final Map<String, String> named) {
    final Matcher matcher = NAMED.matcher(text);
    if (!matcher.find()) {
      return;
    }
    for (final String specifier : matcher.group(1).split(",")) {
      final String[] parts = specifier.trim().replaceFirst("^type\\s+", "").split("\\s+as\\s+");
      final String imported = parts[0].trim();
      final String local = parts[parts.length - 1].trim();
      if (!imported.isEmpty()) {
        named.put(local, matcher.group(2) + "." + imported);
      }
    }
  }
}
