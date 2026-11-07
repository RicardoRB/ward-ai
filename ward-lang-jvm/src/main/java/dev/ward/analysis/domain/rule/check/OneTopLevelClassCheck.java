package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;

/**
 * Reports each top-level type after the first one in a file. The first type may share the name of
 * the file; every later one has to move to a file of its own.
 */
public final class OneTopLevelClassCheck implements RuleCheck {

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the name of the extra type
   */
  public OneTopLevelClassCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return DesignMembers.types(root).stream()
        .filter(DesignMembers::topLevel)
        .skip(1)
        .map(
            visit ->
                CheckSupport.at(
                    visit.node().span(),
                    CheckSupport.fill(message, JUnitMembers.name(visit.node()), "")))
        .toList();
  }
}
