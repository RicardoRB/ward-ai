package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each {@code return} of an array field, which hands out the internal array. A name that a
 * parameter or local shadows is not checked, since the syntax tree does not bind names.
 */
public final class MethodReturnsInternalArrayCheck implements UnitCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the field
   */
  public MethodReturnsInternalArrayCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("classBody"))) {
      final Set<String> arrays = ClassFields.arrayNames(visit.node());
      for (final SyntaxNode returned : SyntaxWalk.descendants(visit.node())) {
        if (isReturnOf(returned, arrays)) {
          final String field = SyntaxWalk.text(returned.children().get(1));
          matches.add(CheckSupport.at(returned.span(), CheckSupport.fill(message, field, "")));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isReturnOf(final SyntaxNode node, final Set<String> arrays) {
    final List<SyntaxNode> children = node.children();
    return "statement".equals(node.kind())
        && children.size() == 3
        && "'return'".equals(children.get(0).kind())
        && "expression".equals(children.get(1).kind())
        && arrays.contains(SyntaxWalk.text(children.get(1)));
  }
}
