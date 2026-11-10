package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method whose first statement calls the method itself, with or without a {@code return}
 * before the call, so the call can never end.
 */
public final class SelfRecursiveMethodCheck implements UnitCheck {
  private static final String METHOD = "methodDeclaration";
  private static final String BODY = "methodBody";
  private static final String IDENTIFIER = "identifier";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the method
   */
  public SelfRecursiveMethodCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(METHOD))) {
      if (callsItselfFirst(visit.node())) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean callsItselfFirst(final SyntaxNode method) {
    final String name =
        method.children().stream()
            .filter(child -> IDENTIFIER.equals(child.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .orElse("");
    final String body =
        method.children().stream()
            .filter(child -> BODY.equals(child.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .orElse("");
    return !name.isEmpty()
        && (body.startsWith("{" + name + "(") || body.startsWith("{return" + name + "("));
  }
}
