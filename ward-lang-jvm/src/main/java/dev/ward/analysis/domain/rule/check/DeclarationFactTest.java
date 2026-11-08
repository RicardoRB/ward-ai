package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.SpanFacts;

/** Decides, from the syntax and the type facts of a declaration, whether it is reported. */
@FunctionalInterface
public interface DeclarationFactTest {

  /**
   * Tells whether the declaration is reported; unknown facts must give {@code false}.
   *
   * @param declaration the declaration node
   * @param facts the facts at the declaration's span
   * @return whether to report it
   */
  boolean reports(SyntaxNode declaration, SpanFacts facts);
}
