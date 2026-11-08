package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeShape;

/**
 * A class that implements {@code java.lang.annotation.Annotation} by hand and does not declare both
 * {@code equals(Object)} and {@code hashCode()}.
 */
public final class AnnotationImplementation implements DeclarationFactTest {

  private static final String ANNOTATION = "java.lang.annotation.Annotation";

  @Override
  public boolean reports(final SyntaxNode declaration, final SpanFacts facts) {
    if (facts.shape() == null
        || facts.members() == null
        || facts.shape().kind() == TypeShape.Kind.ANNOTATION
        || facts.shape().kind() == TypeShape.Kind.INTERFACE) {
      return false;
    }
    final boolean annotation =
        facts.members().ancestors().stream().anyMatch(type -> ANNOTATION.equals(type.name()));
    return annotation && !(declares(facts, "equals", 1) && declares(facts, "hashCode", 0));
  }

  private static boolean declares(final SpanFacts facts, final String name, final int arity) {
    return facts.members().methods().stream().anyMatch(method -> isNamed(method, name, arity));
  }

  private static boolean isNamed(final CallTarget method, final String name, final int arity) {
    return name.equals(method.name()) && method.parameters().size() == arity;
  }
}
