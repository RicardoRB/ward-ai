package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** Reads the methods a class declares: the name, whether it is static and the parameter types. */
final class DeclaredMethods {

  private static final String CLASS_BODY = "classBody";
  private static final String MODIFIER = "modifier";
  private static final String PARAMETER = "formalParameter";
  private static final String TYPE_TYPE = "typeType";

  /**
   * One method of a class.
   *
   * @param name the name of the method
   * @param node the method declaration
   * @param isStatic whether the method is static
   * @param parameterTypes the simple names of the parameter types, with {@code []} for arrays
   */
  record Method(String name, SyntaxNode node, boolean isStatic, List<String> parameterTypes) {}

  private DeclaredMethods() {}

  /** Returns the methods of {@code classDeclaration}, in source order. */
  static List<Method> of(final SyntaxNode classDeclaration) {
    return classDeclaration.children().stream()
        .filter(child -> CLASS_BODY.equals(child.kind()))
        .flatMap(body -> JUnitMembers.members(body).stream())
        .map(DeclaredMethods::method)
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<Method> method(final SyntaxNode member) {
    return JUnitMembers.method(member)
        .map(
            declaration ->
                new Method(
                    JUnitMembers.name(declaration),
                    declaration,
                    member.children().stream()
                        .anyMatch(
                            child ->
                                MODIFIER.equals(child.kind())
                                    && "static".equals(SyntaxWalk.text(child))),
                    parameterTypes(declaration)));
  }

  private static List<String> parameterTypes(final SyntaxNode method) {
    return SyntaxWalk.descendants(method).stream()
        .filter(node -> PARAMETER.equals(node.kind()))
        .map(
            parameter ->
                parameter.children().stream()
                    .filter(child -> TYPE_TYPE.equals(child.kind()))
                    .findFirst()
                    .map(type -> simpleName(SyntaxWalk.text(type)))
                    .orElse(""))
        .toList();
  }

  /** Returns the simple name of a type written in source, without type arguments. */
  static String simpleName(final String written) {
    final String erased = written.replaceAll("<[^<>]*>", "").replaceAll("<[^<>]*>", "");
    final int dot = erased.lastIndexOf('.');
    return dot < 0 ? erased : erased.substring(dot + 1);
  }
}
