package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each method whose throws clause names more exceptions than the limit. Private methods are
 * left alone unless the check says otherwise.
 */
public final class ThrowsCountCheck implements RuleCheck {

  private static final String PRIVATE = "private";
  private static final String THROWS_LIST = "qualifiedNameList";
  private static final String EXCEPTION = "qualifiedName";

  private final int max;
  private final boolean ignorePrivateMethods;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the most exceptions a throws clause may name
   * @param ignorePrivateMethods whether private methods are not checked
   * @param message the message; {@code {value}} is the count and {@code {max}} the limit
   */
  public ThrowsCountCheck(final int max, final boolean ignorePrivateMethods, final String message) {
    this.max = max;
    this.ignorePrivateMethods = ignorePrivateMethods;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit :
        SyntaxWalk.visits(root, Set.of("methodDeclaration", "interfaceMethodDeclaration"))) {
      final SyntaxNode method = visit.node();
      final long count = throwsCount(method);
      if (count > max
          && !(ignorePrivateMethods && DesignMembers.modifiers(visit).contains(PRIVATE))) {
        matches.add(
            CheckSupport.at(
                method.span(),
                CheckSupport.fill(message, String.valueOf(count), String.valueOf(max))));
      }
    }
    return List.copyOf(matches);
  }

  private static long throwsCount(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> THROWS_LIST.equals(child.kind()))
        .flatMap(list -> list.children().stream())
        .filter(child -> EXCEPTION.equals(child.kind()))
        .count();
  }
}
