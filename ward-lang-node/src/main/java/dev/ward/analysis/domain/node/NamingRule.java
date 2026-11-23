package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reports a declared function, variable or parameter whose name contains an underscore between its
 * letters, as ESLint core {@code camelcase} does. Leading and trailing underscores are allowed, and
 * an all-caps name such as {@code MAX_SIZE} is allowed too.
 *
 * @param definition the rule this check was built from
 */
public record NamingRule(RuleDefinition definition) implements CompiledRule {
  private static final String EDGE_UNDERSCORES = "^_+|_+$";
  private static final String PROPERTY = "propertyAssignment";
  private static final String ASSIGNABLE = "assignable";
  private static final String OBJECT_LITERAL = "objectLiteral";

  /** Nodes whose first identifier is a name the rule checks. */
  private static final Set<String> DECLARATION =
      Set.of("functionDeclaration", "variableDeclaration", "parameter", "formalParameterArg");

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(SyntaxTrees.withoutErrors(root), matches);
    return matches;
  }

  private static void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    if (DECLARATION.contains(node.kind())) {
      declaredNames(node).forEach(name -> addIfUnderscored(name, matches));
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, matches);
    }
  }

  /**
   * The names a declaration introduces. In an object pattern the key of {@code {iss_position:
   * issPosition}} is the property read, so only the name after the colon is declared.
   */
  private static List<SyntaxNode> declaredNames(final SyntaxNode declaration) {
    final SyntaxNode pattern = declaration.children().isEmpty() ? null : objectPattern(declaration);
    if (pattern == null) {
      return Collections.singletonList(SyntaxTrees.firstIdentifier(declaration));
    }
    final List<SyntaxNode> names = new ArrayList<>();
    for (final SyntaxNode property : pattern.children()) {
      if (PROPERTY.equals(property.kind()) && !property.children().isEmpty()) {
        names.add(
            SyntaxTrees.firstIdentifier(property.children().get(property.children().size() - 1))
        );
      }
    }
    return names;
  }

  private static SyntaxNode objectPattern(final SyntaxNode declaration) {
    final SyntaxNode first = declaration.children().get(0);
    if (!ASSIGNABLE.equals(first.kind()) || first.children().isEmpty()) {
      return null;
    }
    final SyntaxNode literal = first.children().get(0);
    return OBJECT_LITERAL.equals(literal.kind()) ? literal : null;
  }

  private static void addIfUnderscored(final SyntaxNode identifier, final List<RuleMatch> matches) {
    if (identifier == null || !isUnderscored(identifier.fields().get("text"))) {
      return;
    }
    matches.add(
        new RuleMatch(
            identifier.span(),
            "Identifier '" + identifier.fields().get("text") + "' is not camelCase"
        )
    );
  }

  private static boolean isUnderscored(final String name) {
    final String inner = name.replaceAll(EDGE_UNDERSCORES, "");
    return inner.contains("_") && !inner.equals(inner.toUpperCase(Locale.ROOT));
  }
}
