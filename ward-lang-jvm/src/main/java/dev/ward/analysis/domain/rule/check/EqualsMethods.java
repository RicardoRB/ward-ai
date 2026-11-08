package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Optional;

/** Reads the {@code equals} and {@code hashCode} methods a class declares and inherits. */
final class EqualsMethods {

  private static final String TYPE_TYPE = "typeType";
  private static final String EXTENDS = "'extends'";
  private static final String EQUALS = "equals";
  private static final String OBJECT = "java.lang.Object";
  private static final String ABSTRACT = "abstract";

  private EqualsMethods() {}

  static Optional<SyntaxNode> find(final List<TypeMethods.Member> own, final boolean object) {
    return own.stream()
        .filter(m -> EQUALS.equals(TypeMethods.name(m.method())))
        .filter(
            m -> {
              final List<String> params = TypeMethods.parameterTypes(m.method());
              return params.size() == 1 && TypeMethods.isObject(params.get(0)) == object;
            })
        .map(m -> identifier(m.method()))
        .findFirst();
  }

  static Optional<SyntaxNode> declared(
      final List<TypeMethods.Member> own, final String name, final int parameters) {
    return own.stream()
        .filter(m -> name.equals(TypeMethods.name(m.method())))
        .filter(m -> TypeMethods.parameterTypes(m.method()).size() == parameters)
        .map(m -> identifier(m.method()))
        .findFirst();
  }

  static SyntaxNode identifier(final SyntaxNode method) {
    return TypeMethods.identifier(method).orElse(method);
  }

  /** Returns the {@code equals} whose only parameter has the type of the class itself. */
  static Optional<SyntaxNode> covariant(
      final SyntaxNode type, final List<TypeMethods.Member> own, final List<SyntaxNode> ancestors) {
    final String name = ClassMembers.nameOf(type);
    final boolean isAbstract =
        MemberDeclarations.wrapper(ancestors)
            .map(MemberDeclarations::modifiers)
            .filter(modifiers -> modifiers.contains(ABSTRACT))
            .isPresent();
    return isAbstract
        ? Optional.empty()
        : own.stream()
            .filter(m -> EQUALS.equals(TypeMethods.name(m.method())))
            .filter(m -> TypeMethods.parameterTypes(m.method()).equals(List.of(name)))
            .map(m -> identifier(m.method()))
            .findFirst();
  }

  /** Tells whether an inherited method with the name is declared by {@code Object} or elsewhere. */
  static boolean inherits(
      final TypeMembers members, final String name, final int parameters, final boolean object) {
    return members.inheritedMethods().stream()
        .filter(m -> name.equals(m.name()) && !m.isStatic())
        .filter(m -> m.parameters().size() == parameters)
        .filter(m -> parameters == 0 || OBJECT.equals(m.parameters().get(0).name()))
        .map(CallTarget::declaringType)
        .anyMatch(declaring -> OBJECT.equals(declaring.name()) == object);
  }

  static boolean hasInstanceField(final SyntaxNode type) {
    return ClassMembers.fieldMembers(type).stream()
        .anyMatch(
            field -> !MemberDeclarations.modifiers(field).contains(MemberDeclarations.STATIC));
  }

  static boolean abstractSuperclass(final SyntaxNode type, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = type.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if (EXTENDS.equals(parts.get(index).kind())
          && TYPE_TYPE.equals(parts.get(index + 1).kind())) {
        return types.shapeOf(parts.get(index + 1).span()).filter(TypeShape::isAbstract).isPresent();
      }
    }
    return false;
  }
}
