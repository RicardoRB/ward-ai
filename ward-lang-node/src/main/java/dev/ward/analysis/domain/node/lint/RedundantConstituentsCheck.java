package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a constituent of a written union or intersection type that does nothing or overrides the
 * others: {@code any}, {@code unknown} or {@code never} beside other members, a literal beside its
 * primitive in a union, and a primitive beside its literal in an intersection. Aliases are not
 * resolved, so only written constituents count.
 */
public final class RedundantConstituentsCheck implements RuleCheck {

  private static final String TYPE = "{type}";
  private static final String ANY = "any";
  private static final String UNKNOWN = "unknown";
  private static final String NEVER = "never";
  private static final Set<String> PRIMITIVES = Set.of("string", "number", "boolean", "bigint");
  private static final Set<String> BOOLEANS = Set.of("true", "false");

  private final String message;

  /** Creates the check with the message of the rule. */
  public RedundantConstituentsCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> out = new ArrayList<>();
    visit(root, out);
    return out;
  }

  private void visit(final SyntaxNode node, final List<RuleMatch> out) {
    final String operator = TypeConstituents.operatorOf(node);
    if (operator == null) {
      node.children().forEach(child -> visit(child, out));
      return;
    }
    final List<SyntaxNode> constituents = TypeConstituents.gather(node, operator);
    final Set<String> texts = textsOf(constituents);
    for (final SyntaxNode constituent : constituents) {
      if (isRedundant(operator, SyntaxText.of(constituent), texts)) {
        out.add(new RuleMatch(constituent.span(), fill(SyntaxText.of(constituent))));
      }
    }
    constituents.forEach(constituent -> visit(constituent, out));
  }

  /** Reports whether a constituent does nothing or overrides the others of its type. */
  private static boolean isRedundant(
      final String operator, final String text, final Set<String> texts) {
    if (ANY.equals(text) || UNKNOWN.equals(text) || NEVER.equals(text)) {
      return texts.size() > 1;
    }
    final Optional<String> primitive = primitiveOf(text);
    if (TypeConstituents.UNION.equals(operator)) {
      return primitive.isPresent() && texts.contains(primitive.get());
    }
    return PRIMITIVES.contains(text)
        && texts.stream().anyMatch(other -> primitiveOf(other).equals(Optional.of(text)));
  }

  private static Set<String> textsOf(final List<SyntaxNode> constituents) {
    final Set<String> texts = new HashSet<>();
    constituents.forEach(constituent -> texts.add(SyntaxText.of(constituent)));
    return texts;
  }

  /** Returns the primitive a literal type belongs to; empty for a primitive or another type. */
  private static Optional<String> primitiveOf(final String text) {
    if (PRIMITIVES.contains(text)) {
      return Optional.empty();
    }
    if (text.startsWith("'") || text.startsWith("\"")) {
      return Optional.of("string");
    }
    if (BOOLEANS.contains(text)) {
      return Optional.of("boolean");
    }
    if (text.matches("-?[0-9]+n")) {
      return Optional.of("bigint");
    }
    return text.matches("-?[0-9].*") ? Optional.of("number") : Optional.empty();
  }

  private String fill(final String text) {
    return message.replace(TYPE, text);
  }
}
