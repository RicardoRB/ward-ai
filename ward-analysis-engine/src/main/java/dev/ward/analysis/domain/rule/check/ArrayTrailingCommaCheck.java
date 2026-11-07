package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each array initializer whose closing brace is on a later line than its last element and
 * has no trailing comma, as Checkstyle's ArrayTrailingComma does. With the user option, every array
 * initializer with elements must end with a comma.
 */
public final class ArrayTrailingCommaCheck implements UnitCheck {

  private static final String COMMA = "','";
  private static final String CLOSE = "'}'";

  private final boolean alwaysDemand;
  private final String message;

  /**
   * Creates the check.
   *
   * @param alwaysDemand whether a trailing comma is required even on one line
   * @param message the message reported at each closing brace without a trailing comma
   */
  public ArrayTrailingCommaCheck(final boolean alwaysDemand, final String message) {
    this.alwaysDemand = alwaysDemand;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("arrayInitializer"))) {
      final List<SyntaxNode> children = visit.node().children();
      if (children.size() < 3) {
        continue;
      }
      final SyntaxNode close = children.get(children.size() - 1);
      final SyntaxNode last = children.get(children.size() - 2);
      if (!CLOSE.equals(close.kind()) || COMMA.equals(last.kind()) || "'{'".equals(last.kind())) {
        continue;
      }
      if (alwaysDemand || close.span().startLine() > last.span().endLine()) {
        matches.add(CheckSupport.at(close.span(), message));
      }
    }
    return List.copyOf(matches);
  }
}
