package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an explicit {@code Unit} return type on a function with a block body, which ktlint's
 * {@code no-unit-return} rule asks to leave out.
 */
public final class KotlinUnitReturnCheck implements RuleCheck {

  private static final String FUN = "functionDeclaration";
  private static final String TYPE = "type";
  private static final String FUNCTION_BODY = "functionBody";
  private static final String BLOCK = "block";
  private static final String UNIT = "Unit";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the return type
   */
  public KotlinUnitReturnCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(FUN))) {
      final SyntaxNode function = visit.node();
      if (hasUnitType(function) && hasBlockBody(function)) {
        function.children().stream()
            .filter(child -> TYPE.equals(child.kind()))
            .forEach(type -> matches.add(CheckSupport.at(type.span(), message)));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean hasUnitType(final SyntaxNode function) {
    return function.children().stream()
        .anyMatch(child -> TYPE.equals(child.kind()) && UNIT.equals(SyntaxWalk.text(child)));
  }

  private static boolean hasBlockBody(final SyntaxNode function) {
    return function.children().stream()
        .filter(child -> FUNCTION_BODY.equals(child.kind()))
        .flatMap(body -> body.children().stream())
        .anyMatch(child -> BLOCK.equals(child.kind()));
  }
}
