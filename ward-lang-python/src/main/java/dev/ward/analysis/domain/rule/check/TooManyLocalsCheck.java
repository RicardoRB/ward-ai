package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports a function that binds more distinct local names than {@code max}, like Pylint R0914. */
public final class TooManyLocalsCheck implements RuleCheck {

  private static final Set<String> FUNCTIONS = Set.of("funcdef");

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the largest allowed number of local names, parameters included
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   */
  public TooManyLocalsCheck(final int max, final String message) {
    this.max = max;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final Set<String> locals = locals(visit.node());
      if (locals.size() > max) {
        found.add(new RuleMatch(visit.node().span(), fill(locals.size())));
      }
    }
    return List.copyOf(found);
  }

  private static Set<String> locals(final SyntaxNode function) {
    final Set<String> locals = new HashSet<>(FunctionBodies.parameterNames(function));
    locals.addAll(FunctionBodies.assignedNames(function));
    return locals;
  }

  private String fill(final int count) {
    return message.replace("{value}", String.valueOf(count)).replace("{max}", String.valueOf(max));
  }
}
