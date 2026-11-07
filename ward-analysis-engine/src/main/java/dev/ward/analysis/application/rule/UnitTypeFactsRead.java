package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.TypeNeed;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.Set;

/** Reads the type facts of one unit that a rule asks for, merged into one request. */
final class UnitTypeFactsRead {

  private UnitTypeFactsRead() {}

  /**
   * Returns the facts of {@code unit} that {@code rule} asks for; a rule with no needs, or a
   * language without a resolver, gets none.
   */
  static UnitTypeFacts of(
      final TypeFactsRun types,
      final CompiledRule rule,
      final SourceUnit unit,
      final SyntaxNode root) {
    final Set<TypeNeed> needs = rule.typeNeeds();
    if (needs.isEmpty()) {
      return UnitTypeFacts.none();
    }
    return types
        .facts(unit.language())
        .map(
            facts ->
                new TypeFactsPrePass(facts)
                    .run(
                        unit,
                        root,
                        needs.stream()
                            .map(need -> new FactNeed(need.nodeKinds(), need.facts()))
                            .toList()))
        .orElseGet(UnitTypeFacts::none);
  }
}
