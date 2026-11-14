package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Reads the shape of a Kotlin call: its name, its arguments, and whether it makes an object. */
final class KotlinCalls {

  private static final String CALL_SUFFIX = "callSuffix";
  private static final String SIMPLE = "simpleIdentifier";
  private static final String VALUE_ARGUMENTS = "valueArguments";
  private static final String VALUE_ARGUMENT = "valueArgument";
  private static final String EXPRESSION = "expression";
  private static final String IS = "is";
  private static final int SINGLE = 1;

  private KotlinCalls() {}

  /** Drops the {@code ?} of a nullable type, which no import names. */
  static String erasedNullability(final String written) {
    return written.replaceAll("\\?+$", "");
  }

  static boolean startsWithCall(final List<SyntaxNode> operations) {
    return !operations.isEmpty() && KotlinFlowSyntax.child(operations.get(0), CALL_SUFFIX) != null;
  }

  static String nameOf(final SyntaxNode atom) {
    final List<SyntaxNode> parts = KotlinFlowSyntax.parts(atom);
    return "atomicExpression".equals(atom.kind())
            && parts.size() == SINGLE
            && SIMPLE.equals(parts.get(0).kind())
        ? KotlinFlowSyntax.text(parts.get(0))
        : "";
  }

  /**
   * The Java getter that Kotlin shows as the property {@code name}: {@code parameterMap} is {@code
   * getParameterMap()}, and {@code isSecure} is {@code isSecure()}.
   */
  static String javaGetter(final String name) {
    final boolean isForm =
        name.length() > IS.length() && name.startsWith(IS) && Character.isUpperCase(name.charAt(2));
    return isForm ? name : "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  static boolean isConstructor(final String name, final String qualifier) {
    return Character.isUpperCase(name.charAt(0))
        && (qualifier.isEmpty() || qualifier.matches("[a-z][a-z0-9_.]*"));
  }

  /** The argument expressions of a call, named ones by their value; trailing lambdas are not. */
  static List<SyntaxNode> argumentsOf(final SyntaxNode suffix) {
    final List<SyntaxNode> values = new ArrayList<>();
    final SyntaxNode arguments = KotlinFlowSyntax.child(suffix, VALUE_ARGUMENTS);
    if (arguments != null) {
      for (final SyntaxNode argument : KotlinFlowSyntax.children(arguments, VALUE_ARGUMENT)) {
        values.add(KotlinFlowSyntax.child(argument, EXPRESSION));
      }
    }
    return values;
  }
}
