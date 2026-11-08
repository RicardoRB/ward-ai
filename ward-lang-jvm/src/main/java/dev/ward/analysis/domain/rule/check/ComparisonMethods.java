package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;

/** Tells whether a method is a {@code compareTo(T)} or {@code compare(T, T)} that returns int. */
final class ComparisonMethods {

  private static final int COMPARE_TO_PARAMETERS = 1;
  private static final int COMPARE_PARAMETERS = 2;

  private ComparisonMethods() {}

  static boolean isCompareTo(final TypeMethods.Member member) {
    return signature(member, "compareTo", COMPARE_TO_PARAMETERS);
  }

  static boolean isCompare(final TypeMethods.Member member) {
    return signature(member, "compare", COMPARE_PARAMETERS);
  }

  private static boolean signature(
      final TypeMethods.Member member, final String name, final int parameters) {
    final SyntaxNode method = member.method();
    return name.equals(TypeMethods.name(method))
        && TypeMethods.parameterTypes(method).size() == parameters
        && "int".equals(returnType(method))
        && MemberDeclarations.modifiers(member.declaration()).contains("public");
  }

  private static String returnType(final SyntaxNode method) {
    return JavaNodes.kid(method, "typeTypeOrVoid").map(SyntaxWalk::text).orElse("");
  }
}
