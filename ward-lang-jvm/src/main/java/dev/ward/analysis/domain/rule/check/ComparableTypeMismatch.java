package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import java.util.Optional;

/**
 * A class that implements {@code Comparable<T>} where {@code T} is neither the class nor one of its
 * supertypes. A generic class is never reported, because its type variable cannot be told from a
 * type.
 */
public final class ComparableTypeMismatch implements DeclarationFactTest {

  private static final String COMPARABLE = "java.lang.Comparable";
  private static final String IDENTIFIER = "identifier";
  private static final String TYPE_PARAMETERS = "typeParameters";

  @Override
  public boolean reports(final SyntaxNode declaration, final SpanFacts facts) {
    final Optional<String> self =
        declaration.children().stream()
            .filter(child -> IDENTIFIER.equals(child.kind()))
            .findFirst()
            .map(SyntaxWalk::text);
    final boolean generic =
        declaration.children().stream().anyMatch(child -> TYPE_PARAMETERS.equals(child.kind()));
    if (facts.shape() == null || facts.members() == null || self.isEmpty() || generic) {
      return false;
    }
    return facts.shape().supertypes().stream()
        .filter(type -> COMPARABLE.equals(type.name()) && type.arguments().size() == 1)
        .map(type -> type.arguments().get(0))
        .filter(argument -> argument.arrayDepth() == 0)
        .anyMatch(argument -> !relatedTo(self.get(), argument, facts));
  }

  private static boolean relatedTo(
      final String self, final TypeRef argument, final SpanFacts facts) {
    final String simple = argument.name().substring(argument.name().lastIndexOf('.') + 1);
    return simple.equals(self)
        || facts.members().ancestors().stream()
            .anyMatch(ancestor -> ancestor.name().equals(argument.name()));
  }
}
