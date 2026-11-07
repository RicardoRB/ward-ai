package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads the type parameters a Java declaration declares. */
final class JavaTypeParameters {
  private static final String IDENTIFIER = "identifier";

  private JavaTypeParameters() {}

  /** Returns the type parameters declared by the {@code typeParameters} child of {@code node}. */
  static List<TypeParameter> of(final SyntaxNode node, final TypeScope scope) {
    final List<SyntaxNode> declared =
        Syntax.child(node, "typeParameters")
            .map(found -> Syntax.children(found, "typeParameter"))
            .orElse(List.of());
    final TypeScope inner =
        scope.withTypeVariables(declared.stream().map(JavaTypeParameters::nameOf).toList());
    return declared.stream().map(parameter -> typeParameter(parameter, inner)).toList();
  }

  private static String nameOf(final SyntaxNode parameter) {
    return Syntax.text(Syntax.child(parameter, IDENTIFIER).orElse(parameter));
  }

  private static TypeParameter typeParameter(final SyntaxNode node, final TypeScope scope) {
    final List<JType> bounds =
        Syntax.child(node, "typeBound")
            .map(bound -> JavaTypes.typeList(bound, scope))
            .orElse(List.of());
    return new TypeParameter(nameOf(node), bounds);
  }
}
