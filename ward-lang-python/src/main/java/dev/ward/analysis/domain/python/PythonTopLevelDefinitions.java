package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Symbol;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what the unused-definition rules need from a module: the top-level functions and classes it
 * declares, and the identifiers it uses.
 *
 * <p>shortcut: a name is used when any file spells it, with no scope or import resolution, so a
 * same-named local variable hides a dead definition; upgrade by resolving imports per file.
 * Decorated definitions are left out because decorators register them (routes, fixtures, entry
 * points).
 */
final class PythonTopLevelDefinitions {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");
  private static final String FUNCDEF = "funcdef";
  private static final String CLASSDEF = "classdef";
  private static final String FUNCTION = "function";
  private static final String CLASS = "class";
  private static final String NAME = "name";

  private PythonTopLevelDefinitions() {}

  /**
   * The undecorated top-level functions and classes of the module {@code tree}, then the methods,
   * properties and attributes of its top-level classes.
   */
  static List<Symbol> declared(final SyntaxNode tree) {
    final List<Symbol> symbols = new ArrayList<>();
    for (final SyntaxNode statement : PythonExports.topLevelStatements(tree)) {
      final String kind = kindOf(statement);
      if (!kind.isEmpty()) {
        PythonSyntax.children(statement, NAME).stream()
            .limit(1)
            .forEach(
                name ->
                    symbols.add(new Symbol(PythonSyntax.dottedName(name), statement.span(), kind)));
      }
    }
    symbols.addAll(PythonMemberDefinitions.declared(tree));
    return symbols;
  }

  /**
   * Every identifier under {@code tree} except the name a {@code def} or {@code class} introduces,
   * plus the entries of a literal {@code __all__}, which export a name without using it.
   */
  static Set<String> usedNames(final SyntaxNode tree) {
    final Set<String> names = new HashSet<>();
    collect(tree, names);
    PythonExports.declaredAll(tree).ifPresent(names::addAll);
    return names;
  }

  private static String kindOf(final SyntaxNode statement) {
    return switch (statement.kind()) {
      case FUNCDEF -> FUNCTION;
      case CLASSDEF -> CLASS;
      default -> "";
    };
  }

  private static void collect(final SyntaxNode node, final Set<String> names) {
    if (PythonSyntax.COMMENT_KIND.equals(node.kind())) {
      return;
    }
    if (node.children().isEmpty()) {
      final String text = PythonSyntax.text(node);
      if (IDENTIFIER.matcher(text).matches()) {
        names.add(text);
      } else if (PythonFStrings.isFString(text)) {
        // Every word counts, attribute names included, so {self.port} uses the attribute port.
        final Matcher matcher = IDENTIFIER.matcher(text);
        while (matcher.find()) {
          names.add(matcher.group());
        }
      }
      return;
    }
    if (PythonMemberDefinitions.selfStoreName(node).isPresent()) {
      // The stored attribute is a definition, so only the value and any other target are uses.
      node.children().subList(1, node.children().size()).forEach(child -> collect(child, names));
      return;
    }
    boolean skipped = !FUNCDEF.equals(node.kind()) && !CLASSDEF.equals(node.kind());
    for (final SyntaxNode child : node.children()) {
      if (!skipped && NAME.equals(child.kind())) {
        skipped = true;
      } else {
        collect(child, names);
      }
    }
  }
}
