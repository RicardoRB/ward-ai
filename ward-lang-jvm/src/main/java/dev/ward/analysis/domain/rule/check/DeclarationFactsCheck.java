package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports each declaration of the given kinds that a {@link DeclarationFactTest} accepts. */
public final class DeclarationFactsCheck implements RuleCheck {

  private final Set<String> kinds;
  private final FactNeed need;
  private final DeclarationFactTest test;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kinds the node kinds of the declarations
   * @param facts the facts the test reads
   * @param test decides whether a declaration is reported
   * @param message the message reported at the declaration
   */
  public DeclarationFactsCheck(
      final Set<String> kinds,
      final Set<FactKind> facts,
      final DeclarationFactTest test,
      final String message) {
    this.kinds = Set.copyOf(kinds);
    this.need = new FactNeed(this.kinds, facts);
    this.test = Objects.requireNonNull(test, "test");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return need;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, kinds)) {
      final SyntaxNode declaration = visit.node();
      if (test.reports(declaration, types.at(declaration.span()))) {
        matches.add(CheckSupport.at(declaration.span(), message));
      }
    }
    return List.copyOf(matches);
  }
}
