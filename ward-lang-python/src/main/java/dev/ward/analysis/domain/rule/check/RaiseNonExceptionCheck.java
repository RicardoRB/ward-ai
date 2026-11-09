package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code raise} of a class, or of an instance of a class, that the same module defines
 * without any base class: such a class does not inherit from {@code BaseException}. A class with
 * bases, or one from another module, is not reported.
 */
public final class RaiseNonExceptionCheck implements RuleCheck {

  private static final int MIN_PARTS = 2;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public RaiseNonExceptionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, SyntaxNode> definitions = ModuleDefinitions.of(root);
    final Set<String> plain = new java.util.HashSet<>();
    definitions.forEach(
        (name, definition) -> {
          if ("classdef".equals(definition.kind()) && !ModuleDefinitions.hasBases(definition)) {
            plain.add(name);
          }
        });
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("raise_stmt"))) {
      final String operand = operandName(visit.node());
      if (plain.contains(operand)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** The class a raise names, as in {@code raise C} or {@code raise C(...)}; empty otherwise. */
  private static String operandName(final SyntaxNode raise) {
    final List<SyntaxNode> parts = raise.children();
    if (parts.size() < MIN_PARTS) {
      return "";
    }
    final String text = SyntaxWalk.text(parts.get(1));
    final int paren = text.indexOf('(');
    return paren < 0 ? text : text.substring(0, paren);
  }
}
