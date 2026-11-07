package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a catch clause that catches one of the illegal exception types, by the name written in
 * the clause, at the {@code catch} keyword. The user option is {@code illegal-classes}.
 */
public final class IllegalCatchCheck implements UnitCheck {

  private static final String CATCH_TYPE = "catchType";
  private static final String QUALIFIED_NAME = "qualifiedName";

  private final Set<String> illegalClasses;
  private final String message;

  /**
   * Creates the check.
   *
   * @param illegalClasses the exception type names that must not be caught
   * @param message the message; {@code {value}} is the illegal name
   */
  public IllegalCatchCheck(final Set<String> illegalClasses, final String message) {
    this.illegalClasses = Set.copyOf(illegalClasses);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("catchClause"))) {
      for (final SyntaxNode type : visit.node().children()) {
        if (CATCH_TYPE.equals(type.kind())) {
          addIllegal(visit.node(), type, matches);
        }
      }
    }
    return List.copyOf(matches);
  }

  private void addIllegal(
      final SyntaxNode clause, final SyntaxNode catchType, final List<RuleMatch> matches) {
    for (final SyntaxNode name : catchType.children()) {
      final String text = SyntaxWalk.text(name);
      if (QUALIFIED_NAME.equals(name.kind()) && illegalClasses.contains(text)) {
        matches.add(CheckSupport.at(clause.span(), CheckSupport.fill(message, text, "")));
      }
    }
  }
}
