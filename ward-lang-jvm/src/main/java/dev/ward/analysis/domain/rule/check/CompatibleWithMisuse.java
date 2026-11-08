package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import java.util.HashSet;
import java.util.Set;

/**
 * A method with a parameter annotated {@code @CompatibleWith} whose value names no type parameter
 * of the method or of its class.
 */
public final class CompatibleWithMisuse implements DeclarationFactTest {

  @Override
  public boolean reports(final SyntaxNode declaration, final SpanFacts facts) {
    final CallTarget method = facts.call();
    if (method == null) {
      return false;
    }
    final Set<String> known = new HashSet<>(method.typeParameters());
    known.addAll(method.declaringTypeParameters());
    return method.compatibleWith().stream()
        .anyMatch(value -> !value.isEmpty() && !known.contains(value));
  }
}
