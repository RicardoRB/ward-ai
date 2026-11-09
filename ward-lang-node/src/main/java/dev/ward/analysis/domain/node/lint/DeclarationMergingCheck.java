package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reports a class and an interface that share a name in one scope, which merge unsafely. */
public final class DeclarationMergingCheck implements RuleCheck {

  private static final String CLASS = "class";
  private static final String INTERFACE = "interface";
  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String INTERFACE_DECLARATION = "interfaceDeclaration";
  private static final Set<String> SCOPES =
      Set.of("program", "block", "functionBody", "namespaceDeclaration", "classTail");

  private final String message;

  /** Creates the check with the message of the rule. */
  public DeclarationMergingCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> out = new ArrayList<>();
    scan(root, out);
    for (final String kind : SCOPES) {
      for (final SyntaxNode scope : SyntaxText.nodesOf(root, kind)) {
        if (scope != root) {
          scan(scope, out);
        }
      }
    }
    return out;
  }

  private void scan(final SyntaxNode scope, final List<RuleMatch> out) {
    final List<SyntaxNode> names = new ArrayList<>();
    final Map<String, Set<String>> kinds = new HashMap<>();
    collect(scope, scope, names, kinds);
    for (final SyntaxNode name : names) {
      if (kinds.getOrDefault(SyntaxText.of(name), Set.of()).size() == 2) {
        out.add(new RuleMatch(name.span(), message));
      }
    }
  }

  private static void collect(
      final SyntaxNode scope,
      final SyntaxNode node,
      final List<SyntaxNode> names,
      final Map<String, Set<String>> kinds) {
    if (node != scope && SCOPES.contains(node.kind())) {
      return;
    }
    final String keyword = declarationKeyword(node);
    if (keyword != null) {
      final SyntaxNode name = nameOf(node, keyword);
      if (name != null) {
        names.add(name);
        kinds.computeIfAbsent(SyntaxText.of(name), key -> new HashSet<>()).add(keyword);
      }
    }
    node.children().forEach(child -> collect(scope, child, names, kinds));
  }

  private static String declarationKeyword(final SyntaxNode node) {
    if (CLASS_DECLARATION.equals(node.kind())) {
      return CLASS;
    }
    return INTERFACE_DECLARATION.equals(node.kind()) ? INTERFACE : null;
  }

  private static SyntaxNode nameOf(final SyntaxNode declaration, final String keyword) {
    final List<SyntaxNode> parts = declaration.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if (SyntaxText.isToken(parts.get(index), keyword)) {
        return parts.get(index + 1);
      }
    }
    return null;
  }
}
