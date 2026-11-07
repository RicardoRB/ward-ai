package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads which type parameters of a type declaration accept a nullable argument. */
final class NullParameters {

  private NullParameters() {}

  /** Returns, per type parameter of {@code type}, whether its bound is annotated nullable. */
  static List<Boolean> allowed(final SyntaxNode type) {
    final List<Boolean> out = new ArrayList<>();
    NullSyntax.first(type, "typeParameters")
        .ifPresent(
            parameters ->
                SyntaxWalk.visits(parameters, Set.of("typeParameter"))
                    .forEach(visit -> out.add(nullableBound(visit.node()))));
    return out;
  }

  private static boolean nullableBound(final SyntaxNode parameter) {
    return SyntaxWalk.visits(parameter, Set.of("annotation")).stream()
        .anyMatch(visit -> "Nullable".equals(NullSyntax.simpleName(visit.node())));
  }

  /** The class a type or created name refers to, without type arguments. */
  static String baseName(final SyntaxNode node) {
    final String text = SyntaxWalk.text(node);
    final int generic = text.indexOf('<');
    return generic < 0 ? text : text.substring(0, generic);
  }
}
