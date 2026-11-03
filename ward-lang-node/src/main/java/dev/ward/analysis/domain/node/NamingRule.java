package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reports a declared function, variable or parameter whose name contains an underscore between its
 * letters, as ESLint core {@code camelcase} does. Leading and trailing underscores are allowed, and
 * an all-caps name such as {@code MAX_SIZE} is allowed too.
 *
 * @param definition the rule this check was built from
 */
public record NamingRule(RuleDefinition definition) implements CompiledRule {

  private static final String EDGE_UNDERSCORES = "^_+|_+$";

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return matches;
  }

  private static void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (NodeKinds.DECLARATION.contains(node.kind())) {
      addIfUnderscored(SyntaxTrees.firstIdentifier(node), matches);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, matches);
    }
  }

  private static void addIfUnderscored(final SyntaxNode identifier, final List<RuleMatch> matches) {
    if (identifier == null || !isUnderscored(identifier.fields().get("text"))) {
      return;
    }
    matches.add(
        new RuleMatch(
            identifier.span(),
            "Identifier '" + identifier.fields().get("text") + "' is not camelCase"));
  }

  private static boolean isUnderscored(final String name) {
    final String inner = name.replaceAll(EDGE_UNDERSCORES, "");
    return inner.contains("_") && !inner.equals(inner.toUpperCase(Locale.ROOT));
  }
}
