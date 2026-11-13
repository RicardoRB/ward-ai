package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.api.ApiTypeParameter;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads the type parameters of a Kotlin class, function or property with bounds and variance. */
final class KotlinApiTypeParameters {
  private static final String PARAMETERS = "typeParameters";
  private static final String PARAMETER = "typeParameter";
  private static final String CONSTRAINTS = "typeConstraints";
  private static final String CONSTRAINT = "typeConstraint";
  private static final String NAME = "simpleIdentifier";
  private static final String TYPE = "type";

  /** The references inside a declaration and its type parameters in API form. */
  record Declared(KotlinApiRefs refs, List<ApiTypeParameter> parameters) {}

  private KotlinApiTypeParameters() {}

  /** Returns the names of the type parameters {@code owner} declares. */
  static List<String> names(final SyntaxNode owner) {
    return nodes(owner).stream().map(KotlinApiTypeParameters::nameOf).toList();
  }

  /**
   * Returns the references inside {@code owner}, whose scope already has its type variables, and
   * its type parameters. {@code key} separates class from method variables.
   */
  static Declared declare(final SyntaxNode owner, final String key, final KotlinApiRefs outer) {
    final List<SyntaxNode> nodes = nodes(owner);
    final List<TypeParameter> resolved =
        nodes.stream().map(node -> resolved(node, owner, outer)).toList();
    final KotlinApiRefs inner = outer.withRefs(outer.refs().with(key, resolved));
    final List<ApiTypeParameter> found =
        nodes.stream()
            .map(
                node ->
                    new ApiTypeParameter(
                        nameOf(node),
                        bounds(node, owner).stream().map(inner::of).toList(),
                        variance(node)))
            .toList();
    return new Declared(inner, found);
  }

  private static TypeParameter resolved(
      final SyntaxNode node, final SyntaxNode owner, final KotlinApiRefs outer) {
    return new TypeParameter(
        nameOf(node),
        bounds(node, owner).stream()
            .map(bound -> KotlinTypes.type(bound, outer.scope()))
            .flatMap(Optional::stream)
            .toList());
  }

  private static List<SyntaxNode> nodes(final SyntaxNode owner) {
    return Syntax.child(owner, PARAMETERS).stream()
        .flatMap(list -> Syntax.children(list, PARAMETER).stream())
        .toList();
  }

  private static String nameOf(final SyntaxNode parameter) {
    return Syntax.child(parameter, NAME).map(Syntax::text).orElse("*");
  }

  /** The bound written after the colon and the bounds of a {@code where} clause. */
  private static List<SyntaxNode> bounds(final SyntaxNode parameter, final SyntaxNode owner) {
    final List<SyntaxNode> found = new ArrayList<>(Syntax.children(parameter, TYPE));
    Syntax.child(owner, CONSTRAINTS).stream()
        .flatMap(clause -> Syntax.children(clause, CONSTRAINT).stream())
        .filter(
            constraint ->
                Syntax.child(constraint, NAME)
                    .map(Syntax::text)
                    .filter(nameOf(parameter)::equals)
                    .isPresent())
        .forEach(constraint -> found.addAll(Syntax.children(constraint, TYPE)));
    return found;
  }

  private static String variance(final SyntaxNode parameter) {
    final KotlinApiModifiers modifiers = KotlinApiModifiers.of(parameter);
    if (modifiers.has("in")) {
      return "in";
    }
    return modifiers.has("out") ? "out" : "";
  }
}
