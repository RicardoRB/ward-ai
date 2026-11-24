package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/**
 * Reads the receiver of a Kotlin extension function. A call {@code value.f(x)} of {@code fun
 * T.f(y)} passes the value as the receiver, which the body reads as {@code this}, so the taint
 * analysis treats the receiver as a first parameter named {@code this} and the call's receiver as
 * its first argument.
 */
final class KotlinExtensions {
  private static final String DOT = ".";
  private static final String IDENTIFIER = "identifier";

  private KotlinExtensions() {}

  /** The span of the receiver type of a function declaration, or empty for a plain function. */
  static Optional<Span> receiverOf(final SyntaxNode function) {
    final List<SyntaxNode> parts = KotlinFlowSyntax.parts(function);
    for (int index = 1; index < parts.size(); index++) {
      final SyntaxNode part = parts.get(index);
      if (IDENTIFIER.equals(part.kind())) {
        return Optional.empty();
      }
      if (part.children().isEmpty() && DOT.equals(KotlinFlowSyntax.tokenText(part))) {
        return Optional.of(parts.get(index - 1).span());
      }
    }
    return Optional.empty();
  }
}
