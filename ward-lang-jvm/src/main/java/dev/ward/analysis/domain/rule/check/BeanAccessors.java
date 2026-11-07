package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Finds the getter and setter of a bean field, by the JavaBeans naming and the field's type. */
final class BeanAccessors {

  private static final String BOOLEAN = "boolean";
  private static final String GET = "get";
  private static final String IS = "is";
  private static final String SET = "set";
  private static final String FORMAL_PARAMETER = "formalParameter";

  private BeanAccessors() {}

  /** Tells whether a field of {@code type} called {@code name} has a getter and a setter. */
  static boolean complete(final String name, final String type, final List<SyntaxNode> methods) {
    final String cap = capitalized(name);
    final boolean getter =
        methods.stream()
            .anyMatch(
                method ->
                    isGetter(method, cap, type)
                        && ClassMembers.typeText(method).equals(Optional.of(type)));
    final boolean setter =
        methods.stream()
            .anyMatch(
                method ->
                    SET.concat(cap).equals(MemberDeclarations.name(method))
                        && ClassMetrics.parameterCount(method) == 1
                        && parameterType(method).equals(Optional.of(type)));
    return getter && setter;
  }

  private static boolean isGetter(final SyntaxNode method, final String cap, final String type) {
    final String name = MemberDeclarations.name(method);
    final boolean named =
        name.equals(GET.concat(cap)) || BOOLEAN.equals(type) && name.equals(IS.concat(cap));
    return named && ClassMetrics.parameterCount(method) == 0;
  }

  private static Optional<String> parameterType(final SyntaxNode method) {
    return SyntaxWalk.descendants(method).stream()
        .filter(node -> FORMAL_PARAMETER.equals(node.kind()))
        .findFirst()
        .flatMap(ClassMembers::typeText);
  }

  private static String capitalized(final String name) {
    return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
  }
}
