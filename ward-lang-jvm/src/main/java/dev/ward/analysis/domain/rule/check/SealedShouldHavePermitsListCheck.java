package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reports each sealed type that has no permits clause, as Checkstyle does. */
public final class SealedShouldHavePermitsListCheck implements RuleCheck {

  private static final String SEALED = "sealed";
  private static final String PERMITS = "permits";
  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the type name
   */
  public SealedShouldHavePermitsListCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : DesignMembers.types(root)) {
      final SyntaxNode type = visit.node();
      final String name = JUnitMembers.name(type);
      if (DesignMembers.modifiers(visit).contains(SEALED) && !hasPermits(type)) {
        matches.add(CheckSupport.at(type.span(), CheckSupport.fill(message, name, "")));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean hasPermits(final SyntaxNode type) {
    return type.children().stream().anyMatch(child -> PERMITS.equals(SyntaxWalk.text(child)));
  }
}
