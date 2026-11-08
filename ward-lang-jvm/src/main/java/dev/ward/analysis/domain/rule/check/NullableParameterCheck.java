package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a parameter annotated {@code @Nullable} that its method only ever uses as the receiver of
 * a member access, so the method needs it to be non-null. A parameter that the body also compares,
 * passes on or assigns is left alone.
 */
public final class NullableParameterCheck implements RuleCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the parameter name
   */
  public NullableParameterCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit method : SyntaxWalk.visits(root, Set.of("methodDeclaration"))) {
      final List<SyntaxNode> bodies =
          method.node().children().stream().filter(c -> "methodBody".equals(c.kind())).toList();
      for (final SyntaxNode parameter : SyntaxWalk.descendants(method.node())) {
        if ("formalParameter".equals(parameter.kind()) && nullable(parameter)) {
          report(parameter, bodies, matches);
        }
      }
    }
    return List.copyOf(matches);
  }

  private void report(
      final SyntaxNode parameter, final List<SyntaxNode> bodies, final List<RuleMatch> matches) {
    final List<String> names = ClassMembers.fieldNames(parameter);
    if (names.size() != 1 || bodies.isEmpty()) {
      return;
    }
    final String name = names.get(0);
    final List<NullTokens.Occurrence> uses = NullTokens.uses(NullTokens.tokens(bodies), name);
    if (!uses.isEmpty()
        && uses.stream().allMatch(use -> use.use() == NullTokens.Use.RECEIVER)
        && !NullTokens.assigns(NullTokens.tokens(bodies), name)) {
      matches.add(CheckSupport.at(parameter.span(), CheckSupport.fill(message, name, "")));
    }
  }

  private static boolean nullable(final SyntaxNode parameter) {
    return SyntaxWalk.visits(parameter, Set.of("annotation")).stream()
        .anyMatch(visit -> "Nullable".equals(NullSyntax.simpleName(visit.node())));
  }
}
