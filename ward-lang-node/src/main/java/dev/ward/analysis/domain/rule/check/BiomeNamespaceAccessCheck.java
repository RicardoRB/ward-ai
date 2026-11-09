package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a computed access to a namespace import, such as {@code ns["key"]} or {@code ns[key]}
 * after {@code import * as ns}. A dotted access is not reported. shortcut: matches the name only,
 * so a local variable that shadows the namespace is also reported
 */
public final class BiomeNamespaceAccessCheck implements RuleCheck {

  private static final Pattern NAMESPACE_IMPORT = Pattern.compile("^import\\*as([\\w$]+)from");
  private static final String IMPORT = "importStatement";
  private static final String CALL = "singleExpression";
  private static final int INDEX_PARTS = 4;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BiomeNamespaceAccessCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Set<String> namespaces = namespaces(root);
    if (namespaces.isEmpty()) {
      return List.of();
    }
    return SyntaxWalk.visits(root, Set.of(CALL)).stream()
        .filter(visit -> computedAccessOf(visit.node(), namespaces))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static Set<String> namespaces(final SyntaxNode root) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(IMPORT))) {
      final Matcher matcher = NAMESPACE_IMPORT.matcher(SyntaxWalk.text(visit.node()));
      if (matcher.find()) {
        names.add(matcher.group(1));
      }
    }
    return names;
  }

  /** The name followed by an array literal: the grammar's form of {@code name[key]}. */
  private static boolean computedAccessOf(final SyntaxNode node, final Set<String> namespaces) {
    final List<SyntaxNode> parts = node.children();
    return parts.size() == INDEX_PARTS
            && namespaces.contains(SyntaxWalk.text(parts.get(0)))
            && BiomeTree.isKind(parts.get(1), "'['")
        || parts.size() == 2
            && BiomeTree.isKind(parts.get(0), "identifierName")
            && namespaces.contains(SyntaxWalk.text(parts.get(0)))
            && BiomeTree.isKind(parts.get(1), CALL)
            && BiomeTree.hasChild(parts.get(1), "arrayLiteral");
  }
}
