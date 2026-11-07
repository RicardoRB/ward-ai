package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each comma that ends the constants of an enum, as Checkstyle's NoEnumTrailingComma does.
 * The commas between constants belong to the constant list, not to the declaration.
 */
public final class NoEnumTrailingCommaCheck implements UnitCheck {

  private static final String COMMA = "','";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at each trailing comma
   */
  public NoEnumTrailingCommaCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("enumDeclaration"))) {
      for (final SyntaxNode child : visit.node().children()) {
        if (COMMA.equals(child.kind())) {
          matches.add(CheckSupport.at(child.span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }
}
