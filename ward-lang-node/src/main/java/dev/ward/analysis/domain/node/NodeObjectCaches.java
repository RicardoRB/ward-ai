package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the objects that start empty at the top of a file ({@code const cache: T = {}}, not
 * exported) and the place of every other mention of them, so that a function can tell that it is
 * the only code that reads or writes one.
 *
 * <p>shortcut: the scan is by token and name, so a shadowing local of the same name only makes the
 * proof stricter; upgrade when a project hands such an object to other files through a wrapper.
 */
final class NodeObjectCaches {
  /** A place that no function contains: the mention of an exported object. */
  static final int NOWHERE = -1;

  private static final String EXPORT = "export";
  private static final Pattern DECLARATION =
      Pattern.compile("^(?:const|let|var)([A-Za-z_$][\\w$]*)(?::[^=]+)?=\\{\\};?$");

  private NodeObjectCaches() {}

  /** The start offset of every mention of each such object, except its declaration. */
  static Map<String, List<Integer>> of(final SyntaxNode program) {
    final Map<String, Span> declared = new HashMap<>();
    for (final SyntaxNode statement : NodeFlowSyntax.statements(program)) {
      final Matcher declaration = DECLARATION.matcher(NodeFlowSyntax.text(statement));
      if (declaration.matches()) {
        declared.put(declaration.group(1), statement.span());
      }
    }
    if (declared.isEmpty()) {
      return Map.of();
    }
    final NodeTokens tokens = new NodeTokens(NodeLeaves.of(program));
    final Map<String, List<Integer>> mentions = new HashMap<>();
    declared.keySet().forEach(name -> mentions.put(name, new ArrayList<>()));
    for (int index = 0; index < tokens.size(); index++) {
      final int offset = tokens.leaf(index).span().startOffset();
      final Span statement = declared.get(tokens.text(index));
      if (statement != null && !".".equals(tokens.text(index - 1))) {
        final boolean inside = offset >= statement.startOffset() && offset < statement.endOffset();
        if (!inside) {
          mentions.get(tokens.text(index)).add(offset);
        } else if (EXPORT.equals(tokens.text(index - 2))) {
          mentions.get(tokens.text(index)).add(NOWHERE);
        }
      }
    }
    return Map.copyOf(mentions);
  }
}
